package app.n4c.ark

import android.content.Context

data class DavConfig(
    val url: String = "",
    val user: String = "",
    val pass: String = "",
    val connected: Boolean = false
) {
    val ready: Boolean get() = connected && url.isNotBlank()
    val host: String
        get() = url.trim().removePrefix("https://").removePrefix("http://").substringBefore('/')
}

object ArkPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("ark", Context.MODE_PRIVATE)

    fun onboarded(ctx: Context) = sp(ctx).getBoolean("onboarded", false)
    fun setOnboarded(ctx: Context) = sp(ctx).edit().putBoolean("onboarded", true).apply()

    fun dynamic(ctx: Context) = sp(ctx).getBoolean("dynamic", false)
    fun setDynamic(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("dynamic", v).apply()

    fun cloud(ctx: Context): DavConfig = sp(ctx).let {
        DavConfig(
            url = it.getString("dav_url", "") ?: "",
            user = it.getString("dav_user", "") ?: "",
            pass = it.getString("dav_pass", "") ?: "",
            connected = it.getBoolean("dav_connected", false)
        )
    }

    fun saveCloud(ctx: Context, c: DavConfig) {
        sp(ctx).edit()
            .putString("dav_url", c.url.trim())
            .putString("dav_user", c.user)
            .putString("dav_pass", c.pass)
            .putBoolean("dav_connected", c.connected)
            .apply()
    }
}
