package com.shield.adultblocker

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var btnAccessibility: Button
    private lateinit var btnSafeSearch: Button
    private lateinit var btnAdmin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val backgroundGradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#030B1B"),
                Color.parseColor("#0F172A"),
                Color.parseColor("#1E1B4B")
            )
        )

        val scrollView = ScrollView(this).apply {
            background = backgroundGradient
            isFillViewport = true
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 100, 56, 80)
        }

        val logoGlassFrame = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = createGlassBox("#22FFFFFF", "#66FBBF24", 64f)
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(280, 280).apply {
                bottomMargin = 36
            }
            clipToOutline = true
        }

        val logoImage = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.CENTER_CROP
            scaleX = 1.65f
            scaleY = 1.65f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        logoGlassFrame.addView(logoImage)

        val titleText = TextView(this).apply {
            text = "ACHIVITY"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }

        val subtitleText = TextView(this).apply {
            text = "Just Achieve Your Goal"
            textSize = 14f
            setTextColor(Color.parseColor("#FDE68A"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 40)
        }

        val cautionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassBox("#26EF4444", "#55F87171", 44f)
            setPadding(48, 42, 48, 42)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 52
            }
        }

        val cautionTitle = TextView(this).apply {
            text = "⚠️ PERMANENT LOCK"
            textSize = 13.5f
            setTextColor(Color.parseColor("#FCA5A5"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
        }

        val cautionDesc = TextView(this).apply {
            text = "• Once Accessibility and Uninstall Protection are activated, they CANNOT be turned off or uninstalled from Settings"
            textSize = 13.5f
            setTextColor(Color.parseColor("#FEE2E2"))
            setPadding(0, 14, 0, 0)
            setLineSpacing(10f, 1f)
        }

        cautionCard.addView(cautionTitle)
        cautionCard.addView(cautionDesc)

        btnAccessibility = createStyledButton().apply {
            setOnClickListener {
                if (isAccessibilityEnabled()) {
                    Toast.makeText(this@MainActivity, "Accessibility is already ACTIVE ✅", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }

        btnSafeSearch = createStyledButton().apply {
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "SafeSearch Enforcer is Locked ON ✅",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnAdmin = createStyledButton().apply {
            setOnClickListener {
                if (isDeviceAdminEnabled()) {
                    Toast.makeText(this@MainActivity, "Uninstall Protection is already ACTIVE ✅", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        val compName = ComponentName(this@MainActivity, UninstallProtectionReceiver::class.java)
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Prevents unauthorized uninstallation of Achivity."
                            )
                        }
                        startActivityForResult(intent, 1001)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Admin Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        layout.addView(logoGlassFrame)
        layout.addView(titleText)
        layout.addView(subtitleText)
        layout.addView(cautionCard)
        layout.addView(btnAccessibility)
        layout.addView(btnSafeSearch)
        layout.addView(btnAdmin)

        scrollView.addView(layout)
        setContentView(scrollView)

        updateButtonStates()
        HostsRepository.initialize(this)
    }

    override fun onResume() {
        super.onResume()
        updateButtonStates()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val accActive = isAccessibilityEnabled()
        val adminActive = isDeviceAdminEnabled()

        if (accActive) {
            btnAccessibility.text = "1. Accessibility: ACTIVE ✅"
            btnAccessibility.background = createGlassBox("#2E10B981", "#8034D399", 38f)
        } else {
            btnAccessibility.text = "1. Enable Accessibility"
            btnAccessibility.background = createGlassBox("#2638BDF8", "#667DD3FC", 38f)
        }

        if (accActive) {
            btnSafeSearch.text = "2. SafeSearch Enforcer: ACTIVE ✅"
            btnSafeSearch.background = createGlassBox("#2E10B981", "#8034D399", 38f)
        } else {
            btnSafeSearch.text = "2. SafeSearch Enforcer (Auto-Activates with Accessibility)"
            btnSafeSearch.background = createGlassBox("#1AFFFFFF", "#33FFFFFF", 38f)
        }

        if (adminActive) {
            btnAdmin.text = "3. Uninstall Protection: ACTIVE ✅"
            btnAdmin.background = createGlassBox("#2E10B981", "#8034D399", 38f)
        } else {
            btnAdmin.text = "3. Enable Uninstall Protection"
            btnAdmin.background = createGlassBox("#2638BDF8", "#667DD3FC", 38f)
        }
    }

    private fun createStyledButton(): Button {
        return Button(this).apply {
            isAllCaps = false
            textSize = 14.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(42, 42, 42, 42)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 28
            }
        }
    }

    private fun createGlassBox(fillHex: String, strokeHex: String, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.parseColor(fillHex))
            setStroke(3, Color.parseColor(strokeHex))
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expectedComponent = ComponentName(this, BlockerAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any { ComponentName.unflattenFromString(it) == expectedComponent }
    }

    private fun isDeviceAdminEnabled(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(this, UninstallProtectionReceiver::class.java)
        return dpm.isAdminActive(compName)
    }
}
