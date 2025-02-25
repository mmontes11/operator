package eu.glasskube.operator.mariadb

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.fabric8.kubernetes.api.model.Namespaced
import io.fabric8.kubernetes.api.model.SecretKeySelector
import io.fabric8.kubernetes.client.CustomResource
import io.fabric8.kubernetes.model.annotation.Group
import io.fabric8.kubernetes.model.annotation.Version

data class MariaDBImage(
    var repository: String,
    var tag: String,
    var pullPolicy: String? = null
)

data class MariaDBResourcesRequest(
    val storage: String? = null,
    val cpu: String? = null,
    val memory: String? = null
)

data class MariaDBResources(
    val requests: MariaDBResourcesRequest? = null,
    val limits: MariaDBResourcesRequest? = null
)

data class MariaDBVolumeClaimTemplate(
    var resources: MariaDBResources,
    var storageClassName: String = "standard",
    var accessModes: Collection<String> = listOf("ReadWriteOnce")
)

data class MariaDBSpec(
    var rootPasswordSecretKeyRef: SecretKeySelector,
    var image: MariaDBImage,
    var port: Int = 3306,
    var volumeClaimTemplate: MariaDBVolumeClaimTemplate,
    var metrics: Metrics? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
class MariaDBStatus

@Group("mariadb.mmontes.io")
@Version("v1alpha1")
class MariaDB : CustomResource<MariaDBSpec, MariaDBStatus>(), Namespaced {
    override fun setSpec(spec: MariaDBSpec?) {
        super.setSpec(spec)
    }
}

inline fun mariaDB(block: (@MariaDBDslMarker MariaDB).() -> Unit) =
    MariaDB().apply(block)
