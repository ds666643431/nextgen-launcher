package menu.nextgen.launchera.view.main

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import menu.nextgen.launcher.NextGenCore
import menu.nextgen.launchera.R
import menu.nextgen.launchera.app.VBridge

/**
 * Placeholder launcher for the window between deleting the Kotlin management UI and shipping the
 * native egui one. It exists so the app is openable and the engine is drivable by hand.
 *
 * Deliberately built in code with no layouts, no adapters, no view models and no dependency beyond
 * AppCompat -- everything it does goes through VBridge, so the native launcher can replace this
 * file wholesale without touching anything else.
 */
class BootstrapActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest points at Theme.NextGen.Launch so the splash drawable is already on
        // screen by the time we get here. Swap to the real theme before super, or the splash
        // stays as the window background behind the content.
        setTheme(R.style.Theme_NextGen)
        super.onCreate(savedInstanceState)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply {
            addView(
                root,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })

        render()
    }

    override fun onResume() {
        super.onResume()
        // Sign-in and the all-files prompt both happen in another activity; re-read on return.
        if (!busy) render()
    }

    // ------------------------------------------------------------------ rendering

    private fun render() {
        root.removeAllViews()
        heading("NextGen")
        caption("Placeholder launcher. The native UI replaces this screen.")

        if (!VBridge.hasAllFilesAccess()) {
            caption("All-files access is off. Cloning works without it, but guest file operations will fail.")
            button("Grant all-files access") {
                NextGenCore.get().requestAllFilesAccess(this)
            }
        }

        val account = VBridge.googleAccount()
        heading("Account")
        caption(account?.let { "Google: $it" } ?: "Google: not signed in")
        row(
            "Sign in" to { VBridge.startGoogleSignIn(this) },
            (if (account != null) "Sign out" else null) to { VBridge.googleSignOut(); render() }
        )
        caption(if (VBridge.facebookSignedIn()) "Facebook: connected" else "Facebook: not connected")
        row(
            "Connect" to { VBridge.startFacebookSignIn(this) },
            (if (VBridge.facebookSignedIn()) "Disconnect" else null) to { VBridge.facebookSignOut(); render() }
        )

        heading("Loading…")
        // Both list calls are blocking binder round-trips into :core. Never on this thread.
        work {
            val cloned = VBridge.listCloned(0)
            val installable = VBridge.listInstallable()
            runOnUiThread { renderApps(cloned, installable) }
        }
    }

    private fun renderApps(cloned: Array<String>, installable: Array<String>) {
        // Drop the "Loading..." heading and rebuild the list half.
        root.removeViewAt(root.childCount - 1)

        val clonedPkgs = cloned.map { it.split(VBridge.SEP)[0] }.toSet()

        heading("Cloned (${cloned.size})")
        if (cloned.isEmpty()) {
            caption("Nothing cloned yet. Pick something below.")
        } else {
            cloned.forEach { line ->
                val f = line.split(VBridge.SEP)
                val pkg = f[0]
                val label = f.getOrNull(1).orEmpty().ifEmpty { pkg }
                appRow(label, pkg, "Launch") {
                    work { toastOnUi(if (VBridge.launch(pkg, 0)) "Launching $label" else "Launch failed") }
                }
            }
        }

        val candidates = installable.filter { it.split(VBridge.SEP)[0] !in clonedPkgs }
        heading("Available to clone (${candidates.size})")
        candidates.forEach { line ->
            val f = line.split(VBridge.SEP)
            val pkg = f[0]
            val label = f.getOrNull(1).orEmpty().ifEmpty { pkg }
            appRow(label, pkg, "Clone") {
                work {
                    val error = VBridge.install(pkg, 0)
                    toastOnUi(error?.let { "Clone failed: $it" } ?: "Cloned $label")
                    runOnUiThread { render() }
                }
            }
        }
    }

    // ------------------------------------------------------------------ widgets

    private fun heading(text: String) = root.addView(TextView(this).apply {
        this.text = text
        setTypeface(null, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setPadding(0, dp(22), 0, dp(4))
    })

    private fun caption(text: String) = root.addView(TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(Color.GRAY)
        setPadding(0, 0, 0, dp(6))
    })

    private fun button(text: String, onClick: () -> Unit) = root.addView(Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    })

    /** A left-aligned pair of small buttons; a null label means "omit this one". */
    private fun row(vararg buttons: Pair<String?, () -> Unit>) {
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.forEach { (label, action) ->
            if (label == null) return@forEach
            line.addView(Button(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setOnClickListener { action() }
            })
        }
        if (line.childCount > 0) root.addView(line)
    }

    private fun appRow(label: String, pkg: String, action: String, onClick: () -> Unit) {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        line.addView(TextView(this).apply {
            text = "$label\n$pkg"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        line.addView(Button(this).apply {
            text = action
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener { onClick() }
        })
        root.addView(line, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // ------------------------------------------------------------------ plumbing

    /**
     * Every VBridge call below blocks on a binder round-trip into :core, which sleeps a full
     * second whenever that process is still coming up. One in-flight job at a time.
     */
    private fun work(block: () -> Unit) {
        if (busy) return
        busy = true
        Thread {
            try {
                block()
            } catch (t: Throwable) {
                toastOnUi("Failed: ${t.message}")
            } finally {
                busy = false
            }
        }.start()
    }

    private fun toastOnUi(message: String) = runOnUiThread {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
