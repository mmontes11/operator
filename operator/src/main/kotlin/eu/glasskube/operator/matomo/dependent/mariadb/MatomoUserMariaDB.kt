package eu.glasskube.operator.matomo.dependent.mariadb

import eu.glasskube.kubernetes.api.model.metadata
import eu.glasskube.operator.mariadb.DatabasebRef
import eu.glasskube.operator.mariadb.User
import eu.glasskube.operator.mariadb.UserMariaDBSpec
import eu.glasskube.operator.mariadb.userMariaDB
import eu.glasskube.operator.matomo.Matomo
import eu.glasskube.operator.matomo.MatomoReconciler
import eu.glasskube.operator.matomo.databaseUser
import eu.glasskube.operator.matomo.mariaDBHost
import eu.glasskube.operator.matomo.resourceLabels
import eu.glasskube.operator.matomo.secretName
import io.fabric8.kubernetes.api.model.SecretKeySelector
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent

@KubernetesDependent(labelSelector = MatomoReconciler.SELECTOR)
class MatomoUserMariaDB : CRUDKubernetesDependentResource<User, Matomo>(User::class.java) {
    override fun desired(primary: Matomo, context: Context<Matomo>) = userMariaDB {
        metadata {
            name = primary.databaseUser
            namespace = primary.metadata.namespace
            labels = primary.resourceLabels
        }
        spec = UserMariaDBSpec(
            mariaDbRef = DatabasebRef(primary.mariaDBHost),
            passwordSecretKeyRef = SecretKeySelector("MATOMO_DATABASE_PASSWORD", primary.secretName, null)
        )
    }
}
