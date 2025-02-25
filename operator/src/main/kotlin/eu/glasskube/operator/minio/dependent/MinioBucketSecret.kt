package eu.glasskube.operator.minio.dependent

import eu.glasskube.operator.generic.dependent.GeneratedSecret
import eu.glasskube.operator.minio.MinioBucket
import eu.glasskube.operator.minio.MinioBucketReconciler
import eu.glasskube.operator.minio.bucketName
import eu.glasskube.operator.minio.genericResourceName
import eu.glasskube.operator.minio.resourceLabels
import io.fabric8.kubernetes.api.model.Secret
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition

@KubernetesDependent(labelSelector = MinioBucketReconciler.SELECTOR)
class MinioBucketSecret : GeneratedSecret<MinioBucket>() {
    override val MinioBucket.generatedSecretName get() = genericResourceName
    override val MinioBucket.generatedSecretLabels get() = resourceLabels
    override val MinioBucket.generatedSecretData get() = mapOf(MinioBucket.USERNAME_KEY to defaultUsername)
    override val generatedSecretType = "kubernetes.io/basic-auth"
    override val generatedKeys get() = arrayOf(MinioBucket.PASSWORD_KEY)

    private val MinioBucket.defaultUsername get() = bucketName

    class ReconcilePrecondition : Condition<Secret, MinioBucket> {
        override fun isMet(primary: MinioBucket, secondary: Secret?, context: Context<MinioBucket>?) =
            primary.spec.userSecret == null
    }
}
