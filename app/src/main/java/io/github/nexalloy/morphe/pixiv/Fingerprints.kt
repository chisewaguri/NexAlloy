package io.github.nexalloy.morphe.pixiv

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.opcode

private const val OAUTH_USER = "Ljp/pxv/android/domain/auth/entity/OAuthUser;"
private const val HOME_ILLUSTS = "Ljp/pxv/android/data/home/remote/dto/HomeIllustsResponse;"

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

internal object HomeIllustrationsFingerprint : Fingerprint(
    definingClass = HOME_ILLUSTS,
    returnType = "Ljava/util/List;",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(definingClass = HOME_ILLUSTS, name = "illusts", opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.RETURN_OBJECT, MatchAfterImmediately())
    ),
)

@StrictQuery
internal object HomeIllustrationsNextUrlFingerprint : Fingerprint(
    definingClass = HOME_ILLUSTS,
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(definingClass = HOME_ILLUSTS, name = "nextUrl", opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.RETURN_OBJECT, MatchAfterImmediately())
    ),
)
