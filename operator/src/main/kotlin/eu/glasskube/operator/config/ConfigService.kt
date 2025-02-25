package eu.glasskube.operator.config

import eu.glasskube.kubernetes.api.model.configMap
import eu.glasskube.kubernetes.api.model.metadata
import eu.glasskube.kubernetes.client.getDefaultIngressClass
import eu.glasskube.kubernetes.client.ingressClasses
import eu.glasskube.operator.Environment
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.Resource
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

@Service
class ConfigService(private val kubernetesClient: KubernetesClient) {

    val config: Resource<ConfigMap>
        get() = kubernetesClient.configMaps().inNamespace(Environment.NAMESPACE).withName(ConfigGenerator.NAME)

    operator fun get(key: ConfigKey): String? = config.get().data[key.name]

    fun getValue(key: ConfigKey): String = config.get().data.getValue(key.name)

    /**
     * The Ingress Class name is determined by evaluating the following and picking
     * the first available value:
     * 1. If the Ingress Class name is specified in the operator configuration, it is used
     * 2. If there is exactly one default Ingress Class, its name is used
     * 3. If there is exactly one Ingress Class, its name is used
     * 4. Otherwise, null is used, but it will likely fail!
     *
     * @return the Ingress Class Name to be used for Ingress objects.
     */
    val ingressClassName: String?
        get() = this[ConfigKey.ingressClassName]
            ?: kubernetesClient.getDefaultIngressClass()?.metadata?.name
            ?: kubernetesClient.ingressClasses().list().items.singleOrNull()?.metadata?.name

    val cloudProvider: CloudProvider
        get() = this[ConfigKey.cloudProvider]?.let { CloudProvider.valueOf(it) }
            ?: dynamicCloudProvider

    private val dynamicCloudProvider
        get() = when {
            kubernetesClient.nodes().withLabel("eks.amazonaws.com/nodegroup").list().items.isNotEmpty() ->
                CloudProvider.aws

            kubernetesClient.nodes().withLabel("csi.hetzner.cloud/location").list().items.isNotEmpty() ->
                CloudProvider.hcloud

            else ->
                CloudProvider.generic
        }

    @PostConstruct
    private fun initializeConfigIfNeed() {
        if (!config.isReady) {
            kubernetesClient.resource(
                configMap {
                    metadata {
                        name = ConfigGenerator.NAME
                        namespace = Environment.NAMESPACE
                        labels = mapOf(ConfigGenerator.LABEL_SELECTOR to "")
                    }
                }
            ).create()
        }
    }
}
