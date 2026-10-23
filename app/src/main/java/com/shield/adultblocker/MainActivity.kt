package com.shield.adultblocker

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnSafeSearch: Button
    private lateinit var btnAdmin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 64)
        }

        statusText = TextView(this).apply {
            text = "Syncing GitHub Adult Hosts list..."
            textSize = 16f
            setPadding(0, 0, 0, 48)
        }

        btnAccessibility = Button(this).apply {
            setOnClickListener {
                if (isAccessibilityEnabled()) {
                    Toast.makeText(this@MainActivity, "Millisecond Blocker is already ACTIVE ✅", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }

        btnSafeSearch = Button(this).apply {
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "SafeSearch Enforcer is Locked ON ✅",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnAdmin = Button(this).apply {
            setOnClickListener {
                if (isDeviceAdminEnabled()) {
                    Toast.makeText(this@MainActivity, "Uninstall Protection is already ACTIVE ✅", Toast.LENGTH_SHORT).show()
                } else {
                    val compName = ComponentName(this@MainActivity, UninstallProtectionReceiver::class.java)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents unauthorized uninstallation.")
                    }
                    startActivity(intent)
                }
            }
        }

        layout.addView(statusText)
        layout.addView(btnAccessibility)
        layout.addView(btnSafeSearch)
        layout.addView(btnAdmin)
        setContentView(layout)

        updateButtonStates()

        HostsRepository.initialize(this) { count ->
            runOnUiThread {
                statusText.text = "Active Protection: $count adult domains loaded from GitHub."
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val accActive = isAccessibilityEnabled()
        val adminActive = isDeviceAdminEnabled()

        btnAccessibility.text = if (accActive) {
            "1. Acessibility: ACTIVE ✅"
        } else {
            "1. Enable Acessibility"
        }

        btnSafeSearch.text = if (accActive) {
            "2. SafeSearch Enforcer: ACTIVE ✅"
        } else {
            "2. SafeSearch Enforcer (Auto-Activates with Accessibility)"
        }

        btnAdmin.text = if (adminActive) {
            "3. Uninstall Protection: ACTIVE ✅"
        } else {
            "3. Enable Uninstall Protection"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expectedComponent = ComponentName(this, BlockerAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabledServices.split(":").any { ComponentName.unflattenFromString(it) == expectedComponent }
    }

    private fun isDeviceAdminEnabled(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(this, UninstallProtectionReceiver::class.java)
        return dpm.isAdminActive(compName)
    }
}
