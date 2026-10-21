package io.github.nexalloy.morphe.warp

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.findMethodListDirect
import org.luckypray.dexkit.query.enums.StringMatchType

internal object AccountDataConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/cloudflare/app/data/warpapi/AccountData;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf(
        "Ljava/lang/String;",
        "Lcom/cloudflare/app/data/warpapi/WarpPlusState;",
        "Ljava/lang/Long;",
        "Ljava/lang/Long;",
        "Lorg/threeten/bp/LocalDateTime;",
        "Ljava/lang/Integer;",
        "Ljava/lang/String;",
        "Lcom/cloudflare/app/data/warpapi/Managed;",
        "Lcom/cloudflare/app/data/warpapi/AccountRole;",
    ),
)

internal object AnalyticsBundleFingerprint : Fingerprint(
    definingClass = "Lcom/cloudflare/app/domain/analytics/AnalyticsService;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Lcom/cloudflare/app/domain/analytics/AnalyticsService;",
        "Landroid/os/Bundle;",
    ),
)

val firebaseDispatchMethods = findMethodListDirect {
    findMethod {
        matcher {
            declaredClass("com.cloudflare.app.domain.analytics.AnalyticsService$", StringMatchType.StartsWith)
            name = "invoke"
            addInvoke {
                declaredClass("com.google.android.gms.internal.measurement.", StringMatchType.StartsWith)
                name = "n"
            }
        }
    }
}

internal object CertificatePinnerCheckFingerprint : Fingerprint(
    definingClass = "Lokhttp3/CertificatePinner;",
    name = "check",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/util/List;"),
)
