package eu.glasskube.operator.postgres

import io.fabric8.kubernetes.api.model.SecretKeySelector

data class BackupSource(
    val endpointCA: SecretKeySelector
)
