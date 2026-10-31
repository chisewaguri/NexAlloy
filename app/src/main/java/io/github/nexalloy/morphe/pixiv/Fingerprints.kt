package io.github.nexalloy.morphe.pixiv

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.fieldAccess

private const val OAUTH_USER = "Ljp/pxv/android/domain/auth/entity/OAuthUser;"

internal object OAuthUserIsPremiumFingerprint : Fingerprint(
    definingClass = OAUTH_USER,
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(definingClass = OAUTH_USER, name = "isPremium", opcode = Opcode.IGET_BOOLEAN),
    ),
)

internal object OkHttpNewCallFingerprint : Fingerprint(
    definingClass = "Lokhttp3/OkHttpClient;",
    name = "newCall",
    returnType = "Lokhttp3/Call;",
    parameters = listOf("Lokhttp3/Request;"),
)

internal object PreviewResponseBodyFactoryFingerprint : Fingerprint(
    definingClass = "Lokhttp3/ResponseBody;",
    name = "create",
    returnType = "Lokhttp3/ResponseBody;",
    parameters = listOf("Ljava/lang/String;", "Lokhttp3/MediaType;"),
)
