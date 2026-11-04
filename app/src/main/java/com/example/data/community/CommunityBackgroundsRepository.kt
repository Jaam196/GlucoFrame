package com.example.data.community

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.example.data.network.NetworkClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max

/** One picture offered by the community source. */
data class CommunityPhoto(
    val id: Long,
    val title: String,
    val author: String,
    val license: String,
    val pageUrl: String,
    val thumbUrl: String,
    val downloadUrl: String,
    val width: Int,
    val height: Int
)

data class CommunityPage(val photos: List<CommunityPhoto>, val nextOffset: Int?)

/** Pictures saved on the device, with the credits (author and licence) of each one. */
object CommunityIndex {
    data class Item(
        val id: Long,
        val title: String,
        val author: String,
        val license: String,
        val pageUrl: String,
        val category: String,
        val path: String
    )

    fun baseDir(context: Context): File = File(context.applicationContext.filesDir, "community_backgrounds")

    private fun indexFile(context: Context) = File(baseDir(context), "index.json")

    @Synchronized
    fun load(context: Context): List<Item> {
        return try {
            val file = indexFile(context)
            if (!file.exists()) return emptyList()
            val array = JSONObject(file.readText()).optJSONArray("items") ?: return emptyList()
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Item(
                    id = o.optLong("id"),
                    title = o.optString("title"),
                    author = o.optString("author"),
                    license = o.optString("license"),
                    pageUrl = o.optString("pageUrl"),
                    category = o.optString("category", "naturaleza"),
                    path = o.optString("path")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(context: Context, items: List<Item>) {
        try {
            val array = JSONArray()
            for (item in items) {
                array.put(
                    JSONObject()
                        .put("id", item.id).put("title", item.title).put("author", item.author)
                        .put("license", item.license).put("pageUrl", item.pageUrl)
                        .put("category", item.category).put("path", item.path)
                )
            }
            baseDir(context).mkdirs()
            indexFile(context).writeText(JSONObject().put("items", array).toString())
        } catch (e: Exception) {
            // Credits are best effort; the pictures themselves are already saved
        }
    }
}

/**
 * Free wallpapers made by the community, from Wikimedia Commons. Only files whose licence is CC0 or
 * public domain are offered, so they can be used and redistributed (for example in a Google Play app)
 * without attribution requirements. Author and licence are still kept and shown in the app.
 */
class CommunityBackgroundsRepository(
    private val context: Context,
    private val client: OkHttpClient = NetworkClients.cloudClient
) {
    private val thumbnailCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Searches pictures; [offset] continues a previous search. */
    suspend fun search(terms: String, offset: Int): CommunityPage = withContext(Dispatchers.IO) {
        val search = "$terms filetype:bitmap incategory:\"CC-Zero|PD-self|PD-USGov-NASA|PD-old-100\" -nude -naked -sex -erotic"
        val url = HttpUrl.Builder()
            .scheme("https").host("commons.wikimedia.org").addPathSegments("w/api.php")
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrnamespace", "6")
            .addQueryParameter("gsrsearch", search)
            .addQueryParameter("gsrlimit", "40")
            .addQueryParameter("gsroffset", offset.toString())
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|size|mime|extmetadata")
            .addQueryParameter("iiurlwidth", "480")
            .addQueryParameter("iiextmetadatafilter", "LicenseShortName|Artist|ObjectName")
            .build()

        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("El servidor respondió ${response.code}")
            response.body?.string() ?: throw IOException("Respuesta vacía")
        }

        val root = JSONObject(body)
        val pages = root.optJSONObject("query")?.optJSONObject("pages")
        val photos = ArrayList<Pair<Int, CommunityPhoto>>()
        if (pages != null) {
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val parsed = parsePage(page) ?: continue
                photos.add(parsed)
            }
        }
        val next = root.optJSONObject("continue")?.optInt("gsroffset", -1) ?: -1
        CommunityPage(photos.sortedBy { it.first }.map { it.second }, if (next > 0) next else null)
    }

    private fun parsePage(page: JSONObject): Pair<Int, CommunityPhoto>? {
        val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return null
        val mime = info.optString("mime")
        if (mime != "image/jpeg" && mime != "image/png") return null

        val width = info.optInt("width")
        val height = info.optInt("height")
        // Wide, large pictures only: they look good as wallpapers
        if (width < 1600 || width < height * 1.15) return null

        val meta = info.optJSONObject("extmetadata")
        val license = meta?.optJSONObject("LicenseShortName")?.optString("value").orEmpty()
        if (!isFreeLicense(license)) return null

        val thumb = info.optString("thumburl")
        val original = info.optString("url")
        if (thumb.isBlank() || original.isBlank()) return null

        // Ask for a 1920 px version instead of the (possibly huge) original
        val downloadUrl = if (width <= 1920) original else thumb.replace(Regex("/\\d+px-"), "/1920px-")

        val rawTitle = meta?.optJSONObject("ObjectName")?.optString("value").orEmpty()
        val fileTitle = page.optString("title").removePrefix("File:").substringBeforeLast('.')
        val title = stripHtml(rawTitle).ifBlank { fileTitle }.replace('_', ' ').take(80)
        val author = stripHtml(meta?.optJSONObject("Artist")?.optString("value").orEmpty()).ifBlank { "Autor desconocido" }.take(80)

        return Pair(
            page.optInt("index", Int.MAX_VALUE),
            CommunityPhoto(
                id = page.optLong("pageid"),
                title = title,
                author = author,
                license = license,
                pageUrl = info.optString("descriptionurl"),
                thumbUrl = thumb,
                downloadUrl = downloadUrl,
                width = width,
                height = height
            )
        )
    }

    /** True only for CC0 and public-domain licences. */
    private fun isFreeLicense(license: String): Boolean {
        val l = license.trim().lowercase()
        return l.startsWith("cc0") || l.contains("public domain") || l.startsWith("pd")
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]*>"), " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
            .replace(Regex("\\s+"), " ").trim()

    suspend fun loadThumbnail(url: String): Bitmap? = withContext(Dispatchers.IO) {
        thumbnailCache.get(url)?.let { return@withContext it }
        try {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            val bytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.bytes()
            } ?: return@withContext null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) thumbnailCache.put(url, bitmap)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads the picture, shrinks it to at most ~2200 px and saves it. Returns the file path. */
    suspend fun download(photo: CommunityPhoto, category: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(photo.downloadUrl).header("User-Agent", USER_AGENT).build()
        val bytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("El servidor respondió ${response.code}")
            val body = response.body ?: throw IOException("Respuesta vacía")
            if (body.contentLength() > MAX_BYTES) throw IOException("El archivo es demasiado grande")
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) throw IOException("El archivo es demasiado grande")
                    out.write(buffer, 0, n)
                }
            }
            out.toByteArray()
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / sample > 2200) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("La imagen no es válida")

        val dir = File(CommunityIndex.baseDir(context), category).apply { mkdirs() }
        val file = File(dir, "${photo.id}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()

        val items = CommunityIndex.load(context).filter { it.id != photo.id } +
            CommunityIndex.Item(photo.id, photo.title, photo.author, photo.license, photo.pageUrl, category, file.absolutePath)
        CommunityIndex.save(context, items)
        file.absolutePath
    }

    fun delete(item: CommunityIndex.Item) {
        try {
            File(item.path).delete()
        } catch (e: Exception) {
            // Ignore
        }
        CommunityIndex.save(context, CommunityIndex.load(context).filter { it.id != item.id })
    }

    companion object {
        private const val USER_AGENT = "GlucoseFrame/1.0 (Android app; community wallpapers; CC0 and public domain only)"
        private const val MAX_BYTES = 15 * 1024 * 1024
    }
}
