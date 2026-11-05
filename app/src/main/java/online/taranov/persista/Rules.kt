package online.taranov.persista

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Rule(val pkg: String, val keyword: String, val sound: String? = null) {
    fun matches(pkg: String, title: String, text: String): Boolean =
        this.pkg == pkg && (keyword.isBlank() ||
            title.contains(keyword, ignoreCase = true) ||
            text.contains(keyword, ignoreCase = true))
}

object Rules {
    private const val PREFS = "rules"
    private const val KEY = "list"
    val DEFAULT = listOf(
        Rule("com.google.android.calendar", ""),
        Rule("com.samsung.android.calendar", ""),
    )

    fun load(ctx: Context): List<Rule> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return DEFAULT
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Rule(o.getString("pkg"), o.optString("keyword"), o.optString("sound").ifBlank { null })
        }
    }

    fun save(ctx: Context, rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach {
            arr.put(JSONObject().put("pkg", it.pkg).put("keyword", it.keyword).apply { it.sound?.let { s -> put("sound", s) } })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
