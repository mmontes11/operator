package eu.glasskube.operator.gitea.dependent

import eu.glasskube.kubernetes.api.model.metadata
import eu.glasskube.kubernetes.api.model.service
import eu.glasskube.kubernetes.api.model.servicePort
import eu.glasskube.kubernetes.api.model.spec
import eu.glasskube.operator.gitea.Gitea
import eu.glasskube.operator.gitea.GiteaReconciler
import eu.glasskube.operator.gitea.resourceLabelSelector
import eu.glasskube.operator.gitea.resourceLabels
import eu.glasskube.operator.gitea.sshServiceName
import io.fabric8.kubernetes.api.model.IntOrString
import io.fabric8.kubernetes.api.model.Service
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.ResourceIDMatcherDiscriminator
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent
import io.javaoperatorsdk.operator.processing.event.ResourceID

@KubernetesDependent(
    labelSelector = GiteaReconciler.SELECTOR,
    resourceDiscriminator = GiteaSSHService.Discriminator::class
)
class GiteaSSHService : CRUDKubernetesDependentResource<Service, Gitea>(Service::class.java) {
    internal class Discriminator : ResourceIDMatcherDiscriminator<Service, Gitea>({ ResourceID(it.sshServiceName) })

    override fun desired(primary: Gitea, context: Context<Gitea>) = service {
        metadata {
            name = primary.sshServiceName
            namespace = primary.metadata.namespace
            labels = primary.resourceLabels
        }
        spec {
            type = "LoadBalancer"
            selector = primary.resourceLabelSelector
            ports = listOf(
                servicePort {
                    port = 22
                    name = "ssh"
                    targetPort = IntOrString(22)
                }
            )
        }
    }
}
