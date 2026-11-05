package online.taranov.persista

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object AppPicker {
    private data class App(val pkg: String, val label: String)

    private sealed interface Item
    private data class Header(val text: String) : Item
    private data class FromRecent(val seen: Seen) : Item
    private data class FromApps(val app: App) : Item
    private data object Manual : Item

    // Filled from the loader thread too.
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = HashMap<String, Drawable>()

    // App labels follow this app's language, not the system one.
    @Volatile private var labelsLocale: Locale? = null

    fun label(ctx: Context, pkg: String): String {
        val locale = ctx.resources.configuration.locales[0]
        if (locale != labelsLocale) { labels.clear(); labelsLocale = locale }
        return labels.getOrPut(pkg) {
            val pm = ctx.packageManager
            runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
        }
    }

    fun icon(ctx: Context, pkg: String): Drawable = icons.getOrPut(pkg) {
        val pm = ctx.packageManager
        runCatching { pm.getApplicationIcon(pkg) }.getOrElse { pm.defaultActivityIcon }
    }

    fun show(activity: Activity, onRule: (Rule) -> Unit) {
        val recent = Recent.list()
        var apps = emptyList<App>()
        var query = ""

        val adapter = object : BaseAdapter() {
            var items = emptyList<Item>()
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun isEnabled(position: Int) = items[position] !is Header
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                when (val item = items[position]) {
                    is Header -> TextView(activity).apply {
                        text = item.text
                        setTypeface(typeface, Typeface.BOLD)
                        setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 4))
                    }
                    is FromRecent -> row(
                        activity, icon(activity, item.seen.pkg), label(activity, item.seen.pkg),
                        listOf(item.seen.title, DateFormat.format("HH:mm", item.seen.time)).filter { it.isNotBlank() }
                            .joinToString(" · ")
                    )
                    is FromApps -> row(activity, icon(activity, item.app.pkg), item.app.label, item.app.pkg)
                    Manual -> row(activity, null, activity.getString(R.string.picker_manual), "")
                }

            fun rebuild() {
                val q = query.trim()
                fun hit(vararg s: String) = q.isEmpty() || s.any { it.contains(q, ignoreCase = true) }
                val r = recent.filter { hit(it.pkg, it.title, label(activity, it.pkg)) }
                val a = apps.filter { hit(it.pkg, it.label) }
                items = buildList {
                    if (r.isNotEmpty()) {
                        add(Header(activity.getString(R.string.picker_recent)))
                        r.forEach { add(FromRecent(it)) }
                    }
                    add(Header(activity.getString(
                        if (apps.isEmpty() && q.isEmpty()) R.string.picker_all_loading else R.string.picker_all
                    )))
                    a.forEach { add(FromApps(it)) }
                    add(Manual)
                }
                notifyDataSetChanged()
            }
        }
        adapter.rebuild()

        val search = EditText(activity).apply { hint = activity.getString(R.string.picker_search); isSingleLine = true }
        val list = ListView(activity).apply { this.adapter = adapter }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), 0, dp(activity, 16), 0)
            addView(search)
            addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (activity.resources.displayMetrics.heightPixels * 0.6).toInt()
            ))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.picker_title)
            .setView(box)
            .setNegativeButton(R.string.cancel, null)
            .show()

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s.toString()
                adapter.rebuild()
            }
        })
        list.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            when (val item = adapter.items[position]) {
                is FromRecent -> ruleDialog(activity, item.seen.pkg, item.seen.title, onRule)
                is FromApps -> ruleDialog(activity, item.app.pkg, "", onRule)
                Manual -> ruleDialog(activity, null, "", onRule)
                is Header -> {}
            }
        }

        // Labels of ~150 apps take a few hundred ms: load off the UI thread.
        Thread {
            val pm = activity.packageManager
            val loaded = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != activity.packageName }
                .map { App(it, label(activity, it)) }
                .sortedBy { it.label.lowercase() }
            activity.runOnUiThread {
                apps = loaded
                adapter.rebuild()
            }
        }.start()
    }

    private fun ruleDialog(activity: Activity, pkg: String?, keyword: String, onRule: (Rule) -> Unit) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), 0, dp(activity, 20), 0)
        }
        val pkgField = EditText(activity).apply { hint = activity.getString(R.string.rule_pkg_hint); isSingleLine = true }
        if (pkg == null) box.addView(pkgField)
        val kw = EditText(activity).apply {
            hint = activity.getString(R.string.rule_keyword_hint)
            isSingleLine = true
            setText(keyword)
            setSelectAllOnFocus(true)
        }
        box.addView(kw)
        box.addView(TextView(activity).apply {
            text = activity.getString(R.string.rule_keyword_empty)
            alpha = 0.6f
            textSize = 12f
        })
        AlertDialog.Builder(activity)
            .setTitle(pkg?.let { label(activity, it) } ?: activity.getString(R.string.rule_new))
            .apply { pkg?.let { setIcon(icon(activity, it)) } }
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val p = pkg ?: pkgField.text.toString().trim()
                if (p.isNotEmpty()) onRule(Rule(p, kw.text.toString().trim()))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        kw.requestFocus()
    }

    fun row(ctx: Context, icon: Drawable?, title: String, subtitle: String): View =
        LinearLayout(ctx).apply {
            val pad = dp(ctx, 8)
            setPadding(pad, pad, pad, pad)
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(ImageView(ctx).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 12), 0, 0, 0)
                addView(TextView(ctx).apply { text = title; textSize = 16f })
                if (subtitle.isNotEmpty()) addView(TextView(ctx).apply {
                    text = subtitle
                    textSize = 12f
                    alpha = 0.6f
                    maxLines = 2
                })
            })
        }

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
