package com.example.expensetracker.data.cloud

import com.example.expensetracker.data.preferences.DriveAuthorizationPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import android.util.Log
import com.example.expensetracker.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

data class DriveBackupMetadata(val fileId: String, val modifiedTime: String?, val sizeBytes: Long? = null)

class DriveHttpException(val statusCode: Int, message: String) : IOException(message)

/** Manual operations for the single JSON backup stored in Drive's hidden appDataFolder. */
class GoogleDriveRepository(private val preferences: DriveAuthorizationPreferences) {
    companion object {
        private const val TAG = "GoogleDriveRepository"
        const val BACKUP_FILE_NAME = "expense_tracker_backup.json"
        private const val API = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files"
    }

    suspend fun findBackupFile(accessToken: String, refreshAccessToken: suspend () -> String): DriveBackupMetadata? =
        withContext(Dispatchers.IO) {
            val body = requestWithRefresh(accessToken, refreshAccessToken) { token ->
                request(token, "GET", "$API?spaces=appDataFolder&fields=files(id,name,modifiedTime,size)&q=${encode("name = '$BACKUP_FILE_NAME' and trashed = false")}")
            }
            val files = JSONObject(body).optJSONArray("files") ?: return@withContext null
            if (files.length() == 0) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Backup search: no file found in appDataFolder")
                return@withContext null
            }
            val first = files.getJSONObject(0)
            DriveBackupMetadata(
                first.getString("id"),
                first.optString("modifiedTime").takeIf(String::isNotBlank),
                first.optString("size").toLongOrNull()
            ).also {
                if (BuildConfig.DEBUG) Log.d(TAG, "Backup search: found fileId=${it.fileId}, bytes=${it.sizeBytes ?: "unknown"}")
            }
        }

    suspend fun uploadBackup(
        json: String,
        accessToken: String,
        refreshAccessToken: suspend () -> String
    ): DriveBackupMetadata = withContext(Dispatchers.IO) {
        var fileId = preferences.statusFirstFileId()
        if (fileId == null) {
            val existing = findBackupFile(accessToken, refreshAccessToken)
            fileId = existing?.fileId
            if (fileId != null) preferences.saveBackupFile(fileId, existing?.modifiedTime)
        }

        if (fileId != null) {
            try {
                val updated = update(fileId, json, accessToken, refreshAccessToken)
                preferences.saveBackupFile(updated.fileId, updated.modifiedTime)
                return@withContext updated
            } catch (e: DriveHttpException) {
                if (e.statusCode != 404) throw e
                preferences.clearBackupFileId()
            }
        }

        // A stale local ID can have been removed; search again before creating to avoid duplicates.
        val existing = findBackupFile(accessToken, refreshAccessToken)
        val createdOrUpdated = if (existing != null) {
            try {
                update(existing.fileId, json, accessToken, refreshAccessToken)
            } catch (e: DriveHttpException) {
                if (e.statusCode != 404) throw e
                create(json, accessToken, refreshAccessToken)
            }
        } else create(json, accessToken, refreshAccessToken)
        preferences.saveBackupFile(createdOrUpdated.fileId, createdOrUpdated.modifiedTime)
        createdOrUpdated
    }

    suspend fun downloadBackup(
        accessToken: String,
        refreshAccessToken: suspend () -> String
    ): Pair<String, DriveBackupMetadata> = withContext(Dispatchers.IO) {
        var fileId = preferences.statusFirstFileId()
        var metadata: DriveBackupMetadata? = null
        if (fileId == null) {
            metadata = findBackupFile(accessToken, refreshAccessToken)
            fileId = metadata?.fileId ?: throw DriveHttpException(404, "No cloud backup was found.")
            preferences.saveBackupFile(fileId, metadata.modifiedTime)
        }

        var downloadFileId = fileId!!
        val content = try {
            requestWithRefresh(accessToken, refreshAccessToken) { token ->
                request(token, "GET", "$API/${encodePath(downloadFileId)}?alt=media")
            }
        } catch (e: DriveHttpException) {
            if (e.statusCode != 404) throw e
            preferences.clearBackupFileId()
            metadata = findBackupFile(accessToken, refreshAccessToken)
            val replacementId = metadata?.fileId ?: throw DriveHttpException(404, "No cloud backup was found.")
            downloadFileId = replacementId
            preferences.saveBackupFile(replacementId, metadata?.modifiedTime)
            requestWithRefresh(accessToken, refreshAccessToken) { token ->
                request(token, "GET", "$API/${encodePath(replacementId)}?alt=media")
            }
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "Downloaded backup fileId=$downloadFileId, bytes=${content.toByteArray(StandardCharsets.UTF_8).size}")
        val actualMetadata = metadata ?: findBackupFile(accessToken, refreshAccessToken)
            ?.takeIf { it.fileId == downloadFileId }
        if (actualMetadata != null) preferences.saveBackupFile(actualMetadata.fileId, actualMetadata.modifiedTime)
        content to (actualMetadata ?: DriveBackupMetadata(downloadFileId, null))
    }

    private suspend fun update(
        fileId: String,
        json: String,
        accessToken: String,
        refreshAccessToken: suspend () -> String
    ): DriveBackupMetadata {
        requestWithRefresh(accessToken, refreshAccessToken) { token ->
            request(token, "PATCH", "$UPLOAD_API/${encodePath(fileId)}?uploadType=media&fields=id,name,modifiedTime,size", json, "application/json; charset=utf-8")
        }
        return fetchMetadata(fileId, accessToken, refreshAccessToken)
    }

    private suspend fun create(
        json: String,
        accessToken: String,
        refreshAccessToken: suspend () -> String
    ): DriveBackupMetadata {
        val boundary = "ExpenseTracker-${System.nanoTime()}"
        val metadata = JSONObject()
            .put("name", BACKUP_FILE_NAME)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .toString()
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(json)
            append("\r\n--$boundary--")
        }
        val response = requestWithRefresh(accessToken, refreshAccessToken) { token ->
            request(token, "POST", "$UPLOAD_API?uploadType=multipart&fields=id,name,modifiedTime", body, "multipart/related; boundary=$boundary")
        }
        val created = JSONObject(response)
        return DriveBackupMetadata(created.getString("id"), created.optString("modifiedTime").takeIf(String::isNotBlank), created.optString("size").toLongOrNull())
    }

    private suspend fun fetchMetadata(
        fileId: String,
        accessToken: String,
        refreshAccessToken: suspend () -> String
    ): DriveBackupMetadata {
        val response = requestWithRefresh(accessToken, refreshAccessToken) { token ->
            request(token, "GET", "$API/${encodePath(fileId)}?fields=id,name,modifiedTime,size")
        }
        val json = JSONObject(response)
        return DriveBackupMetadata(json.getString("id"), json.optString("modifiedTime").takeIf(String::isNotBlank), json.optString("size").toLongOrNull())
    }

    private suspend fun requestWithRefresh(
        initialToken: String,
        refreshAccessToken: suspend () -> String,
        request: suspend (String) -> String
    ): String = try {
        request(initialToken)
    } catch (first: DriveHttpException) {
        if (first.statusCode != 401) throw first
        request(refreshAccessToken())
    }

    private fun request(
        token: String,
        method: String,
        url: String,
        body: String? = null,
        contentType: String? = null
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/json")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            if (BuildConfig.DEBUG) Log.d(TAG, "$method Drive request returned HTTP $status")
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("error", "Drive request failed ($status)") }
                    .getOrDefault("Drive request failed ($status)")
                throw DriveHttpException(status, message)
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun DriveAuthorizationPreferences.statusFirstFileId(): String? = status.first().backupFileId

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun encodePath(value: String): String = encode(value).replace("+", "%20")
}
