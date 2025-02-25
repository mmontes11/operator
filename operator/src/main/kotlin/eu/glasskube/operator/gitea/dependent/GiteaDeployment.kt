package eu.glasskube.operator.gitea.dependent

import eu.glasskube.kubernetes.api.model.apps.ROLLING_UPDATE
import eu.glasskube.kubernetes.api.model.apps.deployment
import eu.glasskube.kubernetes.api.model.apps.selector
import eu.glasskube.kubernetes.api.model.apps.spec
import eu.glasskube.kubernetes.api.model.apps.strategy
import eu.glasskube.kubernetes.api.model.apps.template
import eu.glasskube.kubernetes.api.model.configMapRef
import eu.glasskube.kubernetes.api.model.container
import eu.glasskube.kubernetes.api.model.containerPort
import eu.glasskube.kubernetes.api.model.env
import eu.glasskube.kubernetes.api.model.envFrom
import eu.glasskube.kubernetes.api.model.envVar
import eu.glasskube.kubernetes.api.model.intOrString
import eu.glasskube.kubernetes.api.model.metadata
import eu.glasskube.kubernetes.api.model.persistentVolumeClaim
import eu.glasskube.kubernetes.api.model.secretKeyRef
import eu.glasskube.kubernetes.api.model.secretRef
import eu.glasskube.kubernetes.api.model.spec
import eu.glasskube.kubernetes.api.model.volume
import eu.glasskube.kubernetes.api.model.volumeMount
import eu.glasskube.kubernetes.api.model.volumeMounts
import eu.glasskube.operator.addTo
import eu.glasskube.operator.gitea.Gitea
import eu.glasskube.operator.gitea.GiteaReconciler
import eu.glasskube.operator.gitea.configMapName
import eu.glasskube.operator.gitea.dbClusterName
import eu.glasskube.operator.gitea.deploymentName
import eu.glasskube.operator.gitea.genericResourceName
import eu.glasskube.operator.gitea.iniConfigMapName
import eu.glasskube.operator.gitea.resourceLabelSelector
import eu.glasskube.operator.gitea.resourceLabels
import eu.glasskube.operator.gitea.secretName
import io.fabric8.kubernetes.api.model.HTTPGetAction
import io.fabric8.kubernetes.api.model.IntOrString
import io.fabric8.kubernetes.api.model.Probe
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.ResourceRequirements
import io.fabric8.kubernetes.api.model.SecurityContext
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.ResourceIDMatcherDiscriminator
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent
import io.javaoperatorsdk.operator.processing.event.ResourceID

@KubernetesDependent(
    labelSelector = GiteaReconciler.SELECTOR,
    resourceDiscriminator = GiteaDeployment.Discriminator::class
)
class GiteaDeployment : CRUDKubernetesDependentResource<Deployment, Gitea>(Deployment::class.java) {
    internal class Discriminator : ResourceIDMatcherDiscriminator<Deployment, Gitea>({ ResourceID(it.deploymentName) })

    override fun desired(primary: Gitea, context: Context<Gitea>) = deployment {
        metadata {
            name = primary.deploymentName
            namespace = primary.metadata.namespace
            labels = primary.resourceLabels
        }
        spec {
            replicas = primary.spec.replicas
            strategy(ROLLING_UPDATE) {
                maxSurge = 1.intOrString()
                maxUnavailable = 0.intOrString()
            }
            selector {
                matchLabels = primary.resourceLabelSelector
            }
            template {
                metadata {
                    labels = primary.resourceLabels
                }
                spec {
                    containers = listOf(
                        container {
                            name = "gitea"
                            image = IMAGE
                            resources = ResourceRequirements(
                                mapOf("memory" to Quantity("300", "Mi")),
                                mapOf("memory" to Quantity("200", "Mi"))
                            )
                            ports = listOf(
                                containerPort {
                                    name = "http"
                                    containerPort = 3000
                                },
                                containerPort {
                                    name = "ssh"
                                    containerPort = 22
                                }
                            )
                            envFrom {
                                configMapRef(primary.configMapName, false)
                            }
                            volumeMounts {
                                volumeMount {
                                    name = "data"
                                    mountPath = Gitea.WORK_DIR
                                }
                            }
                            livenessProbe = Probe().apply {
                                httpGet = HTTPGetAction().apply {
                                    path = "/api/healthz"
                                    port = IntOrString("http")
                                }
                                initialDelaySeconds = 200
                                timeoutSeconds = 5
                                periodSeconds = 10
                                successThreshold = 1
                                failureThreshold = 6
                            }
                        }
                    )
                    initContainers = mutableListOf(
                        container {
                            name = "chown-data"
                            image = IMAGE
                            command = listOf("chown")
                            args = listOf("git:git", Gitea.WORK_DIR)
                            volumeMounts {
                                volumeMount {
                                    name = "data"
                                    mountPath = Gitea.WORK_DIR
                                }
                            }
                        },
                        container {
                            name = "environment-to-ini"
                            image = IMAGE
                            command = listOf("/bin/sh")
                            args = listOf("-c", "mkdir -p /data/gitea/conf && environment-to-ini")
                            volumeMounts {
                                volumeMount {
                                    name = "data"
                                    mountPath = Gitea.WORK_DIR
                                }
                            }
                            envFrom {
                                configMapRef(primary.configMapName, false)
                                configMapRef(primary.iniConfigMapName, false)
                                secretRef(primary.secretName, false)
                            }
                            env {
                                envVar("GITEA__database__USER") {
                                    secretKeyRef("${primary.dbClusterName}-app", "username", false)
                                }
                                envVar("GITEA__database__PASSWD") {
                                    secretKeyRef("${primary.dbClusterName}-app", "password", false)
                                }
                            }
                            securityContext = SecurityContext().apply {
                                runAsUser = 1000
                                runAsGroup = 1000
                            }
                        },
                        container {
                            name = "gitea-migrate"
                            image = IMAGE
                            command = listOf("gitea")
                            args = listOf("migrate")
                            volumeMounts {
                                volumeMount {
                                    name = "data"
                                    mountPath = Gitea.WORK_DIR
                                }
                            }
                            envFrom {
                                configMapRef(primary.configMapName, false)
                            }
                            securityContext = SecurityContext().apply {
                                runAsUser = 1000
                                runAsGroup = 1000
                            }
                        }
                    ).apply {
                        primary.spec.adminSecret
                            ?.let { adminSecret ->
                                container {
                                    name = "gitea-admin-user"
                                    image = IMAGE
                                    command = listOf("/bin/sh")
                                    args = listOf(
                                        "-c",
                                        """
                                            gitea admin user create --admin \
                                                --username ${'$'}GITEA_ADMIN_USER \
                                                --password ${'$'}GITEA_ADMIN_PASSWORD \
                                                --email ${'$'}GITEA_ADMIN_EMAIL \
                                                --must-change-password=false \
                                              || gitea admin user change-password \
                                                --username ${'$'}GITEA_ADMIN_USER \
                                                --password ${'$'}GITEA_ADMIN_PASSWORD
                                        """.trimIndent()
                                    )
                                    volumeMounts {
                                        volumeMount {
                                            name = "data"
                                            mountPath = Gitea.WORK_DIR
                                        }
                                    }
                                    envFrom {
                                        configMapRef(primary.configMapName, false)
                                        secretRef(adminSecret.name, false)
                                    }
                                    securityContext = SecurityContext().apply {
                                        runAsUser = 1000
                                        runAsGroup = 1000
                                    }
                                }
                            }
                            ?.addTo(this)
                    }
                    volumes = listOf(
                        volume("data") {
                            persistentVolumeClaim(primary.genericResourceName)
                        }
                    )
                }
            }
        }
    }

    private companion object {
        const val IMAGE = "gitea/gitea:${Gitea.APP_VERSION}"
    }
}
