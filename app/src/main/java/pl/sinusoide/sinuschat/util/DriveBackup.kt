package pl.sinusoide.sinuschat.util

import android.content.Context
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object DriveBackup {

    private const val TAG = "DriveBackup"
    const val SCOPE_FILE = "https://www.googleapis.com/auth/drive.file"
    const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    const val FOLDER = "Backup SinusChat"
    private const val APPDATA = "appDataFolder"
    private val BACKUP_NAME = Regex("^SinusChat-.*\\.(sinuschat|scb)$")

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private fun request(hidden: Boolean) = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(if (hidden) SCOPE_APPDATA else SCOPE_FILE)))
        .build()

    private fun restoreRequest() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE_APPDATA), Scope(SCOPE_FILE)))
        .build()

    sealed class Auth {
        data class Token(val value: String) : Auth()
        data class NeedsUser(val intent: IntentSender) : Auth()
        data class Error(val message: String) : Auth()
    }

    fun authorize(ctx: Context, hidden: Boolean): Auth = authorizeWith(ctx, request(hidden))

    fun authorizeRestore(ctx: Context): Auth = authorizeWith(ctx, restoreRequest())

    private fun authorizeWith(ctx: Context, request: AuthorizationRequest): Auth = try {
        val result: AuthorizationResult =
            Tasks.await(Identity.getAuthorizationClient(ctx.applicationContext).authorize(request),
                45, TimeUnit.SECONDS)
        val pending = result.pendingIntent
        when {
            result.accessToken != null && !result.hasResolution() -> Auth.Token(result.accessToken!!)
            pending != null -> Auth.NeedsUser(pending.intentSender)
            result.accessToken != null -> Auth.Token(result.accessToken!!)
            else -> Auth.Error("brak tokenu")
        }
    } catch (e: Throwable) {
        Log.e(TAG, "authorize", e)
        Auth.Error(describe(e))
    }

    fun tokenFromResult(ctx: Context, data: android.content.Intent?): String? = try {
        Identity.getAuthorizationClient(ctx.applicationContext)
            .getAuthorizationResultFromIntent(data).accessToken
    } catch (e: Throwable) {
        Log.e(TAG, "tokenFromResult", e); null
    }

    fun upload(token: String, file: File, name: String, hidden: Boolean): String {
        val parent = if (hidden) APPDATA else folderId(token)
        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", "application/octet-stream")
            .put("parents", org.json.JSONArray().put(parent))

        val start = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name,parents")
            .header("Authorization", "Bearer $token")
            .header("X-Upload-Content-Type", "application/octet-stream")
            .header("X-Upload-Content-Length", file.length().toString())
            .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        val location = http.newCall(start).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Dysk odmówił startu: ${resp.code}")
            resp.header("Location") ?: throw IOException("Dysk nie podał adresu wysyłki")
        }

        val body: RequestBody = file.asRequestBody("application/octet-stream".toMediaType())
        val put = Request.Builder().url(location)
            .header("Authorization", "Bearer $token")
            .put(body)
            .build()

        return http.newCall(put).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Wysyłka nieudana: ${resp.code}")
            JSONObject(text).optString("id")
        }
    }

    fun describeFile(token: String, id: String): String = try {
        http.newCall(Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$id?fields=id,name,size,spaces,parents")
            .header("Authorization", "Bearer $token").build()).execute().use { resp ->
            resp.body?.string().orEmpty()
        }
    } catch (e: Throwable) { "błąd: ${e.message}" }

    fun prune(token: String, hidden: Boolean, keepId: String) {
        if (keepId.isEmpty()) return
        try {
            val url = if (hidden)
                "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder" +
                    "&orderBy=createdTime desc&fields=files(id,name)&pageSize=50"
            else {
                val q = java.net.URLEncoder.encode("'${folderId(token)}' in parents and trashed = false", "UTF-8")
                "https://www.googleapis.com/drive/v3/files?q=$q" +
                    "&orderBy=createdTime desc&fields=files(id,name)&pageSize=50"
            }
            val list = http.newCall(Request.Builder().url(url)
                .header("Authorization", "Bearer $token").build()).execute().use { resp ->
                if (!resp.isSuccessful) return
                JSONObject(resp.body?.string().orEmpty()).optJSONArray("files")
            } ?: return
            for (i in 0 until list.length()) {
                val f = list.optJSONObject(i) ?: continue
                val id = f.optString("id")
                if (id.isEmpty() || id == keepId || !BACKUP_NAME.matches(f.optString("name"))) continue
                http.newCall(Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$id")
                    .header("Authorization", "Bearer $token")
                    .delete().build()).execute().close()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "porządki na Dysku: ${e.message}")
        }
    }

    data class Remote(val id: String, val name: String, val size: Long, val createdMs: Long, val hidden: Boolean)

    fun findLatest(token: String): Remote? {
        val found = ArrayList<Remote>()
        found += listBackups(token, hidden = true,
            "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder" +
                "&orderBy=createdTime desc&fields=files(id,name,size,createdTime)&pageSize=20")
        findFolderId(token)?.let { folder ->
            val q = java.net.URLEncoder.encode("'$folder' in parents and trashed = false", "UTF-8")
            found += listBackups(token, hidden = false,
                "https://www.googleapis.com/drive/v3/files?q=$q" +
                    "&orderBy=createdTime desc&fields=files(id,name,size,createdTime)&pageSize=20")
        }
        return found.filter { it.name.endsWith(".${BackupManager.EXTENSION}") }.maxByOrNull { it.createdMs }
    }

    private fun listBackups(token: String, hidden: Boolean, url: String): List<Remote> =
        http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("files") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val f = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = f.optString("name")
                if (!BACKUP_NAME.matches(name)) return@mapNotNull null
                val created = try { java.time.Instant.parse(f.optString("createdTime")).toEpochMilli() }
                    catch (_: Exception) { 0L }
                Remote(f.optString("id"), name, f.optString("size").toLongOrNull() ?: 0L, created, hidden)
            }
        }

    fun download(token: String, id: String, dest: File, onBytes: (Long) -> Unit) {
        http.newCall(Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$id?alt=media")
            .header("Authorization", "Bearer $token").build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Pobieranie nieudane: ${resp.code}")
            val body = resp.body ?: throw IOException("Pusta odpowiedź Dysku")
            body.byteStream().use { src ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        onBytes(total)
                    }
                }
            }
        }
    }

    private fun findFolderId(token: String): String? {
        val q = java.net.URLEncoder.encode(
            "mimeType = 'application/vnd.google-apps.folder' and name = '$FOLDER' and trashed = false",
            "UTF-8")
        return http.newCall(Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)&pageSize=1")
            .header("Authorization", "Bearer $token").build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Dysk: ${resp.code}")
            JSONObject(resp.body?.string().orEmpty()).optJSONArray("files")
                ?.optJSONObject(0)?.optString("id")
        }?.takeIf { it.isNotEmpty() }
    }

    private fun folderId(token: String): String {
        findFolderId(token)?.let { return it }

        val body = JSONObject()
            .put("name", FOLDER)
            .put("mimeType", "application/vnd.google-apps.folder")
        return http.newCall(Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Nie udało się założyć folderu: ${resp.code}")
            JSONObject(resp.body?.string().orEmpty()).optString("id")
        }
    }

    private fun describe(e: Throwable): String {
        val api = e as? com.google.android.gms.common.api.ApiException
            ?: (e.cause as? com.google.android.gms.common.api.ApiException)
        return when (api?.statusCode) {
            10 -> "setup"
            16, 12501 -> "cancel"
            else -> e.message ?: e.javaClass.simpleName
        }
    }
}
