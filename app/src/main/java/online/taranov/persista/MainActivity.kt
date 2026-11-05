package online.taranov.persista

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.LocaleManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.TileService
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var p: Ui.Palette
    private val handler = Handler(Looper.getMainLooper())
    // The tile and the pause notification change state while this screen stays resumed under the shade.
    private val stateListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }
    // Rule card that is open while the system sound picker is on top.
    private var openCard: RuleCard? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Ui.palette(this)
        Ui.setupWindow(this, p.bg, lightBars = !Ui.night(this))
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(16))
        }
        setContentView(ScrollView(this).apply {
            clipToPadding = false
            addView(root)
            Ui.applyInsets(this)
        })
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        // Long press on the quick settings tile lands here.
        // Not again when recreated (language change): the activity keeps the tile's intent.
        if (savedInstanceState == null && intent?.action == TileService.ACTION_QS_TILE_PREFERENCES) openPicker()
        // Recreated after a language change: the pause notification and the tile still show the old one.
        Calls.refresh(this)
    }

    fun openPicker() = AppPicker.show(this) { rule ->
        Rules.save(this, Rules.load(this) + rule)
        render()
    }

    override fun onResume() {
        super.onResume()
        Calls.prefs(this).registerOnSharedPreferenceChangeListener(stateListener)
        render()
    }

    override fun onPause() {
        super.onPause()
        Calls.prefs(this).unregisterOnSharedPreferenceChangeListener(stateListener)
        handler.removeCallbacksAndMessages(null)
    }

    private fun render() {
        root.removeAllViews()
        handler.removeCallbacksAndMessages(null)
        val rules = Rules.load(this)

        root.addView(titleRow(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        statusCard(rules.size)
        permissionBanners()

        if (rules.isEmpty()) {
            root.addView(Ui.text(this, getString(R.string.rules_empty), 15f, p.text2)
                .apply { setPadding(dp(4), dp(24), dp(4), dp(8)) })
        } else {
            val labels = rules.map { AppPicker.label(this, it.pkg) }
            val list = Ui.card(this, p.surface).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            rules.forEachIndexed { i, r ->
                if (i > 0) list.addView(Ui.divider(this, p).apply {
                    (layoutParams as LinearLayout.LayoutParams).marginStart = dp(68)
                })
                list.addView(ruleRow(i, r, labels[i], labels.count { it == labels[i] } > 1))
            }
            root.addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) })
        }
        root.addView(Ui.pill(this, getString(R.string.add_rule), p.primaryBg, p.primaryFg, p.press) { openPicker() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(12) })

        if (Build.VERSION.SDK_INT >= 33) root.addView(languageCard(),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(16) })
    }

    private fun titleRow() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(24), 0, dp(20))
        addView(Ui.text(this@MainActivity, getString(R.string.app_name), 30f, p.text, bold = true),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageButton(this@MainActivity).apply {
            setImageResource(R.drawable.ic_help)
            setColorFilter(p.text2)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = getString(R.string.about_title)
            isFocusable = true
            Ui.clickable(this, p.press, 24, null) { showAbout() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(4) })
    }

    private fun showAbout() {
        // AlertDialog's message has its own scroll container; title and close button stay visible.
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_text)
            .setPositiveButton(R.string.about_close, null)
            .show()
    }

    private fun languageCard() = Ui.card(this, p.surface).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(64)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        val title = getString(R.string.language_label)
        val current = languages()[languageIndex()].second
        contentDescription = "$title, $current"
        isFocusable = true
        isScreenReaderFocusable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_language)
            setColorFilter(p.text2)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(Ui.text(this@MainActivity, title, 16f, p.text))
            addView(Ui.text(this@MainActivity, current, 13f, p.text2))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Ui.text(this@MainActivity, "›", 22f, p.text2).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(12) })
        Ui.clickable(this, p.press, 20, p.surface) { chooseLanguage() }
    }

    // Stored by the system for the whole process (listener included) and also editable in the app's system settings.
    private fun languages() = listOf(null to getString(R.string.language_system)) + LANGUAGES

    private fun languageIndex(): Int {
        val current = getSystemService(LocaleManager::class.java).applicationLocales
        val code = if (current.isEmpty) null else current[0].language
        return languages().indexOfFirst { it.first == code }.coerceAtLeast(0)
    }

    private fun chooseLanguage() {
        val options = languages()
        AlertDialog.Builder(this)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(options.map { it.second }.toTypedArray(), languageIndex()) { d, i ->
                d.dismiss()
                getSystemService(LocaleManager::class.java).applicationLocales =
                    options[i].first?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun statusCard(ruleCount: Int) {
        val on = Calls.isOn(this)
        val bg = if (on) p.okBg else p.warnBg
        val fg = if (on) p.okFg else p.warnFg
        val title: String
        val sub: String
        if (on) {
            title = getString(R.string.status_on)
            sub = if (ruleCount == 0) getString(R.string.no_rules) else getString(R.string.rule_count, ruleCount)
        } else {
            val until = Calls.pausedUntil(this)
            handler.postDelayed({ render() }, until - System.currentTimeMillis() + 500)
            title = getString(R.string.paused_until, Calls.time(until))
            sub = getString(R.string.paused_text)
        }
        root.addView(Ui.card(this, bg).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@MainActivity).apply {
                setImageResource(if (on) R.drawable.ic_alarm else android.R.drawable.ic_media_pause)
                setColorFilter(fg)
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(14) })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(this@MainActivity, title, 17f, fg, bold = true))
                addView(Ui.text(this@MainActivity, sub, 13f, fg))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.pill(this@MainActivity, getString(if (on) R.string.pause else R.string.resume), p.surface, fg, p.press) {
                if (on) Calls.chooser(this@MainActivity) { render() }.show()
                else { Calls.resume(this@MainActivity); render() }
            })
        })
    }

    private fun permissionBanners() {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationListenerAccessGranted(ComponentName(this, NotifyListener::class.java)))
            banner(getString(R.string.banner_listener)) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        if (!nm.areNotificationsEnabled())
            banner(getString(R.string.banner_notifications)) {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        if (!nm.canUseFullScreenIntent())
            banner(getString(R.string.banner_fullscreen)) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
    }

    private fun banner(message: String, open: () -> Unit) {
        root.addView(Ui.card(this, p.warnBg).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(this@MainActivity, message, 14f, p.warnFg),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.link(this@MainActivity, getString(R.string.open), p.warnFg, p.press, open))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10) })
    }

    private fun ruleRow(index: Int, r: Rule, label: String, showPkg: Boolean) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(10))
        addView(ImageView(this@MainActivity).apply { setImageDrawable(AppPicker.icon(this@MainActivity, r.pkg)) },
            LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(Ui.text(this@MainActivity, label, 16f, p.text).apply { maxLines = 1 })
                if (r.keyword.isNotBlank()) addView(Ui.chip(this@MainActivity, r.keyword, p),
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { marginStart = dp(8) })
            })
            val details = listOfNotNull(r.pkg.takeIf { showPkg }, "♪ " + soundTitle(r.sound)).joinToString(" · ")
            addView(Ui.text(this@MainActivity, details, 13f, p.text2).apply { maxLines = 2 })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Ui.text(this@MainActivity, "›", 22f, p.text2))
        Ui.clickable(this, p.press, 0, null) { RuleCard(index, r).show() }
    }

    private inner class RuleCard(val index: Int, rule: Rule) {
        var sound = rule.sound
        val label = AppPicker.label(this@MainActivity, rule.pkg)
        val soundLabel = Ui.text(this@MainActivity, soundTitle(sound), 15f, p.text)
        val effect = Ui.text(this@MainActivity, "", 13f, p.text2).apply { setPadding(0, dp(2), 0, 0) }
        val keyword = EditText(this@MainActivity).apply {
            hint = getString(R.string.keyword_example)
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val w = s.toString().trim()
                    effect.text = if (w.isEmpty()) getString(R.string.effect_any, label)
                        else getString(R.string.effect_word, w)
                }
            })
            setText(rule.keyword)
        }
        val dialog: AlertDialog = AlertDialog.Builder(this@MainActivity)
            .setView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(20), dp(24), 0)
                addView(LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ImageView(this@MainActivity).apply { setImageDrawable(AppPicker.icon(this@MainActivity, rule.pkg)) },
                        LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(Ui.text(this@MainActivity, label, 20f, p.text, bold = true))
                        addView(Ui.text(this@MainActivity, rule.pkg, 12f, p.text2))
                    })
                })
                addView(caption(getString(R.string.caption_keyword)))
                addView(keyword)
                addView(effect)
                addView(Ui.divider(this@MainActivity, p).apply {
                    (layoutParams as LinearLayout.LayoutParams).topMargin = dp(18)
                })
                addView(caption(getString(R.string.caption_sound)))
                addView(LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(soundLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(Ui.link(this@MainActivity, "▶", Ui.BRAND, p.press) {
                        Ringer.start(this@MainActivity, getString(R.string.test_title), getString(R.string.sound_test_text), sound, label)
                    })
                    addView(Ui.link(this@MainActivity, getString(R.string.change), Ui.BRAND, p.press) { pickSound(index, sound) })
                })
            })
            .setPositiveButton(R.string.save) { _, _ ->
                val rules = Rules.load(this@MainActivity)
                if (index in rules.indices) Rules.save(this@MainActivity, rules.mapIndexed { j, r ->
                    if (j == index) r.copy(keyword = keyword.text.toString().trim(), sound = sound) else r
                })
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.delete) { _, _ -> confirmDelete(index, label) }
            .create()
            .apply { setOnDismissListener { if (openCard === this@RuleCard) openCard = null } }

        fun show() {
            openCard = this
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(p.danger)
        }

        fun soundPicked(s: String?) {
            sound = s
            soundLabel.text = soundTitle(s)
        }
    }

    private fun caption(s: String) = Ui.text(this, s, 13f, p.text2).apply { setPadding(0, dp(18), 0, 0) }

    private fun confirmDelete(index: Int, label: String) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_confirm, label))
            .setPositiveButton(R.string.delete) { _, _ ->
                Rules.save(this, Rules.load(this).filterIndexed { j, _ -> j != index })
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(p.danger)
    }

    private fun soundTitle(sound: String?): String =
        sound?.let { runCatching { RingtoneManager.getRingtone(this, Uri.parse(it))?.getTitle(this) }.getOrNull() }
            ?: getString(R.string.sound_default)

    private fun pickSound(index: Int, current: String?) {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                current?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            )
        startActivityForResult(intent, REQ_SOUND + index)
    }

    @Deprecated("Activity result API is AndroidX-only")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val index = requestCode - REQ_SOUND
        val rules = Rules.load(this)
        if (resultCode != RESULT_OK || index !in rules.indices) return
        val uri = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        val sound = uri?.takeIf { !RingtoneManager.isDefault(it) }?.toString()
        Rules.save(this, rules.mapIndexed { j, r -> if (j == index) r.copy(sound = sound) else r })
        openCard?.takeIf { it.index == index }?.soundPicked(sound)
        render()
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    companion object {
        private const val REQ_SOUND = 100
        private val LANGUAGES = listOf("ru" to "Русский", "en" to "English")
    }
}
