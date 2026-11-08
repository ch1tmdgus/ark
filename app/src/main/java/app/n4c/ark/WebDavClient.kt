package app.n4c.ark

import android.net.Uri
import android.util.Xml
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.source
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class RemoteFile(val name: String, val size: Long, val modifiedMs: Long)

/** 블로킹 WebDAV 클라이언트 (반드시 백그라운드 스레드에서 호출) */
class WebDavClient(cfg: DavConfig) {

    companion object {
        const val REMOTE_DIR = "ARK"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .retryOnConnectionFailure(false)
        .build()

    private val auth: String? =
        if (cfg.user.isNotBlank()) Credentials.basic(cfg.user, cfg.pass, Charsets.UTF_8) else null

    private val base: HttpUrl = normalize(cfg.url).toHttpUrl()

    private fun normalize(u: String): String {
        val t = u.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    private fun folderUrl(): HttpUrl = base.newBuilder().addPathSegment(REMOTE_DIR).build()
    private fun fileUrl(name: String): HttpUrl = folderUrl().newBuilder().addPathSegment(name).build()

    private fun req(url: HttpUrl): Request.Builder {
        val b = Request.Builder().url(url)
        auth?.let { b.header("Authorization", it) }
        return b
    }

    private fun <T> exec(request: Request, handle: (Response) -> T): T {
        client.newCall(request).execute().use { resp ->
            if (resp.code == 401) throw IOException("401 Unauthorized")
            if (!resp.isSuccessful) throw IOException("${resp.code} ${resp.message}")
            return handle(resp)
        }
    }

    private val propfindBody = (
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<d:propfind xmlns:d=\"DAV:\"><d:prop><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"
        ).toRequestBody("application/xml".toMediaType())

    fun test() {
        val r = req(base).method("PROPFIND", propfindBody).header("Depth", "0").build()
        exec(r) { }
    }

    fun ensureFolder() {
        val r = req(folderUrl()).method("MKCOL", null).build()
        client.newCall(r).execute().use { resp ->
            when {
                resp.code == 401 -> throw IOException("401 Unauthorized")
                resp.isSuccessful -> {}
                resp.code == 405 -> {} // already exists
                else -> throw IOException("MKCOL ${resp.code} ${resp.message}")
            }
        }
    }

    fun upload(name: String, input: InputStream, length: Long, onProgress: (Long) -> Unit) {
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = length
            override fun writeTo(sink: BufferedSink) {
                input.source().use { src ->
                    val buf = Buffer()
                    var total = 0L
                    var last = 0L
                    while (true) {
                        val n = src.read(buf, 65536L)
                        if (n == -1L) break
                        sink.write(buf, n)
                        total += n
                        if (total - last >= 524288L) { onProgress(total); last = total }
                    }
                    onProgress(total)
                }
            }
        }
        exec(req(fileUrl(name)).put(body).build()) { }
    }

    fun download(name: String, dest: File, onProgress: (Long, Long) -> Unit) {
        exec(req(fileUrl(name)).get().build()) { resp ->
            val body = resp.body ?: throw IOException("empty body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(131072)
                    var read = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (read - last >= 524288L) { onProgress(read, total); last = read }
                    }
                    onProgress(read, total)
                }
            }
        }
    }

    fun readText(name: String): String =
        exec(req(fileUrl(name)).get().build()) { it.body?.string() ?: "" }

    fun delete(name: String) {
        exec(req(fileUrl(name)).delete().build()) { }
    }

    fun listAll(): List<RemoteFile> {
        val r = req(folderUrl()).method("PROPFIND", propfindBody).header("Depth", "1").build()
        val xml = try {
            exec(r) { it.body?.string() ?: "" }
        } catch (e: IOException) {
            if (e.message?.startsWith("404") == true) return emptyList()
            throw e
        }
        return parse(xml)
    }

    private fun parse(xml: String): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(StringReader(xml))
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        var href: String? = null
        var len = 0L
        var mod = 0L
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "response" -> { href = null; len = 0L; mod = 0L }
                    "href" -> href = p.nextText()
                    "getcontentlength" -> len = p.nextText().trim().toLongOrNull() ?: 0L
                    "getlastmodified" -> mod = runCatching { fmt.parse(p.nextText().trim())?.time }.getOrNull() ?: 0L
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "response") {
                val h = href
                if (h != null && !h.endsWith("/")) {
                    out += RemoteFile(Uri.decode(h.substringAfterLast('/')), len, mod)
                }
            }
            ev = p.next()
        }
        return out
    }
}
