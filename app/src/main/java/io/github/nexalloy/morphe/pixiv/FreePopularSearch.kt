package io.github.nexalloy.morphe.pixiv

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.security.MessageDigest

// Pixiv's OkHttp objects belong to its class loader and cannot be cast to the module's copies.
internal class FreePopularSearch(
    loader: ClassLoader,
    private val minimumBookmarks: () -> Int = { 0 },
    private val feed: PopularPicksFeed = PopularPicksFeed(),
) {
    private val clientClass = loader.loadClass("okhttp3.OkHttpClient")
    private val clientBuilderClass = loader.loadClass("okhttp3.OkHttpClient\$Builder")
    private val requestClass = loader.loadClass("okhttp3.Request")
    private val requestBuilderClass = loader.loadClass("okhttp3.Request\$Builder")
    private val responseClass = loader.loadClass("okhttp3.Response")
    private val responseBuilderClass = loader.loadClass("okhttp3.Response\$Builder")
    private val bodyClass = loader.loadClass("okhttp3.ResponseBody")
    private val chainClass = loader.loadClass("okhttp3.Interceptor\$Chain")
    private val interceptorClass = loader.loadClass("okhttp3.Interceptor")
    private val requestUrl = requestClass.getMethod("url")
    private val requestMethod = requestClass.getMethod("method")
    private val requestHeader = requestClass.getMethod("header", String::class.java)
    private val requestBuilder = requestClass.getMethod("newBuilder")
    private val builderUrl = requestBuilderClass.getMethod("url", String::class.java)
    private val buildRequest = requestBuilderClass.getMethod("build")
    private val chainRequest = chainClass.getMethod("request")
    private val chainProceed = chainClass.getMethod("proceed", requestClass)
    private val chainCall = chainClass.getMethod("call")
    private val callCancelled = loader.loadClass("okhttp3.Call").getMethod("isCanceled")
    private val callExecute = loader.loadClass("okhttp3.Call").getMethod("execute")
    private val callCancel = loader.loadClass("okhttp3.Call").getMethod("cancel")
    private val responseCode = responseClass.getMethod("code")
    private val responseBody = responseClass.getMethod("body")
    private val responseBuilder = responseClass.getMethod("newBuilder")
    private val builderBody = responseBuilderClass.getMethod("body", bodyClass)
    private val removeHeader = responseBuilderClass.getMethod("removeHeader", String::class.java)
    private val buildResponse = responseBuilderClass.getMethod("build")
    private val readBody = bodyClass.getMethod("string")
    private val createBody = bodyClass.getMethod("create", String::class.java, loader.loadClass("okhttp3.MediaType"))
    private val responseRequest = responseBuilderClass.getMethod("request", requestClass)
    private val responseProtocol = responseBuilderClass.getMethod("protocol", loader.loadClass("okhttp3.Protocol"))
    private val builderCode = responseBuilderClass.getMethod("code", Int::class.javaPrimitiveType)
    private val builderMessage = responseBuilderClass.getMethod("message", String::class.java)
    private val protocol = loader.loadClass("okhttp3.Protocol").getField("HTTP_1_1").get(null)

    fun matches(request: Any): Boolean = requestMethod.call(request) == "GET" &&
        feed.matches(requestUrl.call(request).toString())

    fun wrapClient(client: Any, originalNewCall: (Any, Any) -> Any): Any {
        val interceptor = Proxy.newProxyInstance(interceptorClass.classLoader, arrayOf(interceptorClass)) { proxy, method, args ->
            when (method.name) {
                "intercept" -> intercept(args!![0], client, originalNewCall)
                "equals" -> proxy === args!![0]
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "NexAlloy popular picks"
                else -> error("Unexpected interceptor method: ${method.name}")
            }
        }
        val builder = clientClass.getMethod("newBuilder").call(client)!!
        @Suppress("UNCHECKED_CAST")
        val interceptors = clientBuilderClass.getMethod("interceptors").call(builder) as MutableList<Any>
        interceptors.add(0, interceptor)
        return clientBuilderClass.getMethod("build").call(builder)!!
    }

    private fun intercept(chain: Any, client: Any, originalNewCall: (Any, Any) -> Any): Any {
        val request = chainRequest.call(chain)!!
        if (!matches(request)) return chainProceed.call(chain, request)!!
        fun sourceRequest(url: String): Any {
            val builder = requestBuilder.call(request)!!
            builderUrl.call(builder, url)
            return buildRequest.call(builder)!!
        }
        val fetch: PicksFetch = { url, cancellation ->
            cancellation.check()
            readResponse(chainProceed.call(chain, sourceRequest(url))!!)
        }
        val prefetch: PicksFetch = { url, cancellation ->
            cancellation.check()
            val call = originalNewCall(client, sourceRequest(url))
            cancellation.attach { callCancel.call(call); Unit }
            try { readResponse(callExecute.call(call)!!) } finally { cancellation.attach(null) }
        }
        val authorization = requestHeader.call(request, "Authorization") as String? ?: ""
        val owner = MessageDigest.getInstance("SHA-256").digest(authorization.toByteArray()).joinToString("") { "%02x".format(it) }
        val json = try {
            feed.load(requestUrl.call(request).toString(), owner, minimumBookmarks(), fetch, prefetch) {
                callCancelled.call(chainCall.call(chain)) == true
            }
        } catch (error: HttpFailure) {
            return replaceBody(responseBuilder.call(error.response)!!, error.body)
        }
        val builder = responseBuilderClass.getConstructor().newInstance()
        responseRequest.call(builder, request)
        responseProtocol.call(builder, protocol)
        builderCode.call(builder, 200)
        builderMessage.call(builder, "OK")
        return replaceBody(builder, json.toString())
    }

    private fun readResponse(response: Any): JsonObject {
        val body = responseBody.call(response) ?: throw IOException("Pixiv returned no response body")
        val text = readBody.call(body) as String
        if (responseCode.call(response) as Int !in 200..299) throw HttpFailure(response, text)
        return try { JsonParser.parseString(text).asJsonObject }
        catch (error: RuntimeException) { throw IOException("Invalid Pixiv search response", error) }
    }

    private fun replaceBody(builder: Any, text: String): Any {
        removeHeader.call(builder, "Content-Length")
        builderBody.call(builder, createBody.call(null, text, null))
        return buildResponse.call(builder)!!
    }

    private class HttpFailure(val response: Any, val body: String) : IOException("Pixiv search failed")
    private companion object {
        fun Method.call(receiver: Any?, vararg args: Any?): Any? = try {
            invoke(receiver, *args)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }
}
