package io.github.nexalloy.morphe.instagram.distraction

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.instanceOf
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcodeEnum
import io.github.nexalloy.morphe.returns
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.strings
import io.github.nexalloy.morphe.writeRegister
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData

private const val SESSION = "Lcom/instagram/common/session/UserSession;"
private const val MEDIA = "Lcom/instagram/feed/media/Media;"
private const val CLIPS_URL_HANDLER = "clips/ClipsUrlHandlerActivity"
private const val CLIPS_LANE_HANDLER = "clipslane/ClipsLaneUrlHandlerActivity"
private val clipsHandlerPackages = listOf("com/instagram/clips/urlhandlers", "com/instagram/urlhandlers")
private val dedicatedClipsHandlers = listOf(
    "clipschaining/IgClipsChainingUrlHandlerActivity", CLIPS_LANE_HANDLER, "clipsapp/ClipsAppUrlHandlerActivity"
)
internal val clipsUrlHandlerClasses = clipsHandlerPackages.flatMap { pkg ->
    (dedicatedClipsHandlers + CLIPS_URL_HANDLER).map { "$pkg/$it".replace('/', '.') }
}

internal object HomeResponseFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    strings = listOf("feed_items", "auto_load_more_enabled", "more_available")
)

internal object ExploreResponseFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    strings = listOf("sectional_items", "clusters", "session_paging_token")
)

private fun responseField(parser: Fingerprint, key: String, type: String, opcode: Opcode) =
    findFieldDirect {
        Fingerprint(
            classFingerprint = parser,
            name = "unsafeParseFromJson",
            filters = listOf(string(key), fieldAccess(type = type, opcode = opcode, location = MatchAfterWithin(45)))
        ).instructionMatches.last().instruction.fieldRef!!
    }

internal val homeItems = responseField(HomeResponseFingerprint, "feed_items", "Ljava/util/List;", Opcode.IPUT_OBJECT)
internal val homeMoreAvailable = responseField(HomeResponseFingerprint, "more_available", "Z", Opcode.IPUT_BOOLEAN)
internal val homeAutoLoadMore = responseField(HomeResponseFingerprint, "auto_load_more_enabled", "Z", Opcode.IPUT_BOOLEAN)
internal val exploreItems = responseField(ExploreResponseFingerprint, "sectional_items", "Ljava/util/List;", Opcode.IPUT_OBJECT)
internal val exploreClusters = responseField(ExploreResponseFingerprint, "clusters", "Ljava/util/List;", Opcode.IPUT_OBJECT)
internal val exploreMoreAvailable = responseField(ExploreResponseFingerprint, "more_available", "Z", Opcode.IPUT_BOOLEAN)

internal val homeRequestParameters = findFieldDirect {
    Fingerprint(
        strings = listOf("FEED_REQUEST_SENT", "pagination_source"),
        filters = listOf(
            string("FEED_REQUEST_SENT"),
            fieldAccess(type = "Ljava/util/Map;", opcode = Opcode.IGET_OBJECT),
            string("pagination_source", location = MatchAfterWithin(2)),
            methodCall(smali = "Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;",
                location = MatchAfterWithin(2))
        )
    ).instructionMatches[1].instruction.fieldRef!!
}

internal object FeedItemSerializerFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "L"),
    strings = listOf("media_or_ad", "clips_netego", "suggested_top_accounts")
)

context(dexkit: DexKitBridge)
private fun itemField(key: String, type: String = "L") =
    Fingerprint(
        classFingerprint = FeedItemSerializerFingerprint,
        returnType = "V",
        parameters = listOf("L", "L"),
        filters = listOf(
            fieldAccess(type = type, opcode = Opcode.IGET_OBJECT),
            string(key, location = MatchAfterWithin(2))
        )
    ).matchAllOrNull()?.single()?.instructionMatches?.first()?.instruction?.fieldRef
        ?: encodedSuggestionField(key)

context(dexkit: DexKitBridge)
private fun encodedSuggestionField(key: String): FieldData {
    val serializer = FeedItemSerializerFingerprint()
    val itemClass = serializer.paramTypeNames.last()
    val code = serializer.instructions
    val reads = code.windowed(3).mapIndexedNotNull { index, (guard, id, call) ->
        val decoder = call.methodRef
        if (guard.opcodeEnum != Opcode.IF_EQZ || id.literal == null || call.opcodeEnum != Opcode.INVOKE_STATIC ||
            decoder?.paramTypeNames != listOf("int") || decoder.returnTypeName != "java.lang.String" ||
            call.registerOrNull(0) != id.registerOrNull(0)
        ) return@mapIndexedNotNull null

        val register = guard.register(0)
        val fieldRead = code.getOrNull(index - 1)?.takeIf { it.writeRegister == register }
            ?: code.getOrNull(index - 2)
        val field = fieldRead?.fieldRef
        if (fieldRead?.opcodeEnum == Opcode.IGET_OBJECT && fieldRead.writeRegister == register &&
            field?.className == itemClass
        ) Triple(field, id.literal, decoder.descriptor) else null
    }
    val decoder = reads.map { it.third }.distinct().mapNotNull { dexkit.getMethodData(it) }
        .single { key in it.usingStrings }
    val instructions = decoder.instructions
    check(instructions.first().opcodeEnum == Opcode.PACKED_SWITCH)
    // The string table emits its default branch first, followed by cases descending to ID zero.
    val cases = instructions.drop(3).takeWhile { it.opcodeEnum != Opcode.NOP }.chunked(2)
    check(cases.all { it.size == 2 && it[0].string != null && it[1].opcodeEnum == Opcode.RETURN_OBJECT })
    val id = cases.asReversed().indexOfFirst { it[0].string == key }.toLong()
    check(id >= 0)
    return reads.single { it.second == id && it.third == decoder.descriptor }.first
}

internal val feedItemMedia = findFieldDirect { itemField("media_or_ad", MEDIA) }
internal val feedItemClips = findFieldDirect { itemField("clips_netego") }
internal val feedItemSuggestions = findFieldListDirect {
    listOf("clips_netego", "stories_netego", "suggested_hashtags", "suggested_shops")
        .map { itemField(it) }
}
internal val feedItemAccountSuggestions = findFieldListDirect {
    listOf(
        "suggested_users", "suggested_top_accounts", "suggested_producers",
        "suggested_producers_v2", "suggested_businesses", "suggested_close_friends"
    ).map { itemField(it) }
}

internal val mediaProductType = findMethodDirect {
    listOf(MEDIA, "Lcom/instagram/feed/media/LiveTreeMediaDict;").firstNotNullOf { owner ->
        Fingerprint(
            definingClass = owner,
            returnType = "Ljava/lang/String;",
            parameters = emptyList(),
            strings = listOf("product_type")
        ).matchAllOrNull()?.single()?.method
    }
}

private object NavigationTabFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf("fragment_feed", "fragment_clips", "fragment_search")
)

internal val navigationTabs = findMethodDirect {
    val tab = NavigationTabFingerprint().declaredClass!!.descriptor
    Fingerprint(
        parameters = listOf(SESSION, "Z"),
        returnType = "Ljava/util/List;",
        filters = listOf(methodCall(definingClass = tab, parameters = listOf("Ljava/lang/String;"), returnType = tab))
    )()
}

internal object ProfileTabsFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf("Lcom/instagram/user/model/User;"),
    strings = listOf("profile_media_grid", "profile_clips", "profile_tagged_media_photos_of_you")
)

internal val reelsLaunchers = findMethodListDirect {
    findMethod {
        matcher {
            returns("V")
            strings("ClipsViewerLauncher.KEY_CONFIG")
            addInvoke { name = "putParcelable" }
        }
    }.filter { method -> method.paramTypeNames.contains("com.instagram.clips.intf.ClipsViewerConfig") }
}

internal val clipsUrlHandler = findMethodDirect {
    clipsHandlerPackages.firstNotNullOfOrNull { pkg ->
        Fingerprint(
            definingClass = "L$pkg/$CLIPS_URL_HANDLER;",
            returnType = "V",
            parameters = listOf("Landroid/os/Bundle;", "Landroid/os/Bundle;", "L"),
            strings = listOf("ClipsUrlHandlerActivity")
        ).matchAllOrNull()?.single()?.method
    } ?: Fingerprint(
        definingClass = "Lcom/instagram/urlhandler/MainSessionUrlHandlerActivity;",
        returnType = "V",
        parameters = listOf("Landroid/os/Bundle;", "Landroid/os/Bundle;", "L"),
        strings = listOf("ClipsUrlHandlerActivity", "InstagramUrlLaunchConstants.EXTRA_SHOULD_PREFETCH_MAIN_FEED")
    )()
}

internal val dedicatedClipsUrlHandlers = findMethodListDirect {
    dedicatedClipsHandlers.map { handler ->
        clipsHandlerPackages.firstNotNullOfOrNull { pkg ->
            Fingerprint(
                definingClass = "L$pkg/$handler;",
                returnType = "V",
                parameters = listOf("Landroid/os/Bundle;", "Landroid/os/Bundle;", SESSION)
            ).matchAllOrNull()?.single()?.method
        } ?: run {
            check(handler == CLIPS_LANE_HANDLER)
            Fingerprint(
                definingClass = "Lcom/instagram/urlhandler/UserSessionUrlHandlerActivity;",
                isStatic = true,
                returnType = "V",
                parameters = listOf(
                    "Lcom/instagram/base/activity/BaseFragmentActivity;", SESSION,
                    "Ljava/lang/Object;", "Landroid/os/Bundle;"
                ),
                filters = listOf(instanceOf("Lcom/instagram/clips/urlhandlers/$CLIPS_LANE_HANDLER;"))
            ).match().method
        }
    }
}
