package app.n4c.ark

import org.json.JSONObject

enum class BackupMode { APK, DATA, BOTH }

enum class Dest { LOCAL, CUSTOM, CLOUD }

data class AppItem(val label: String, val pkg: String, val isSystem: Boolean)

data class OpResult(val ok: Boolean, val title: String, val details: String)

data class BackupMeta(
    val pkg: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val device: String,
    val androidVersion: String,
    val sdk: Int,
    val time: Long,
    val mode: BackupMode,
    val hasExt: Boolean
) {
    fun toJson(): String = JSONObject().apply {
        put("format", 1)
        put("pkg", pkg)
        put("label", label)
        put("versionName", versionName)
        put("versionCode", versionCode)
        put("device", device)
        put("androidVersion", androidVersion)
        put("sdk", sdk)
        put("time", time)
        put("mode", mode.name)
        put("hasExt", hasExt)
    }.toString(2)

    companion object {
        fun parse(text: String): BackupMeta? = runCatching {
            val o = JSONObject(text)
            val pkg = o.getString("pkg")
            BackupMeta(
                pkg = pkg,
                label = o.optString("label", pkg),
                versionName = o.optString("versionName", "?"),
                versionCode = o.optLong("versionCode", 0L),
                device = o.optString("device", "?"),
                androidVersion = o.optString("androidVersion", "?"),
                sdk = o.optInt("sdk", 0),
                time = o.optLong("time", 0L),
                mode = runCatching { BackupMode.valueOf(o.optString("mode", "BOTH")) }.getOrDefault(BackupMode.BOTH),
                hasExt = o.optBoolean("hasExt", false)
            )
        }.getOrNull()
    }
}

/** archive: 로컬은 루트 경로, 클라우드는 서버 파일 이름 */
data class BackupEntry(
    val meta: BackupMeta,
    val archive: String,
    val size: Long,
    val source: Dest
)
