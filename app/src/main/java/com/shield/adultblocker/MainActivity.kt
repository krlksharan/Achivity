package com.shield.adultblocker

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnVpn: Button
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
                    Toast.makeText(
                        this@MainActivity,
                        "Millisecond Accessibility Blocker is already ACTIVE ✅",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }

        btnVpn = Button(this).apply {
            setOnClickListener {
                val intent = VpnService.prepare(this@MainActivity)
                if (intent != null) {
                    startActivityForResult(intent, 100)
                } else {
                    onActivityResult(100, RESULT_OK, null)
                }
            }
        }

        btnAdmin = Button(this).apply {
            setOnClickListener {
                if (isDeviceAdminEnabled()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Uninstall Protection (Device Admin) is already ACTIVE ✅",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val compName = ComponentName(this@MainActivity, UninstallProtectionReceiver::class.java)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Prevents unauthorized uninstallation of ShieldBlocker."
                        )
                    }
                    startActivityForResult(intent, 200)
                }
            }
        }

        layout.addView(statusText)
        layout.addView(btnAccessibility)
        layout.addView(btnVpn)
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
        btnAccessibility.text = if (isAccessibilityEnabled()) {
            "1. Millisecond Accessibility Blocker: ACTIVE ✅"
        } else {
            "1. Enable Millisecond Accessibility Blocker"
        }

        btnVpn.text = if (DnsVpnService.isRunning) {
            "2. SafeSearch & Adult DNS Filter: ACTIVE ✅"
        } else {
            "2. Enable SafeSearch & Adult DNS Filter"
        }

        btnAdmin.text = if (isDeviceAdminEnabled()) {
            "3. Uninstall Protection (Device Admin): ACTIVE ✅"
        } else {
            "3. Enable Uninstall Protection (Device Admin)"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expectedComponent = ComponentName(this, BlockerAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any {
            ComponentName.unflattenFromString(it) == expectedComponent
        }
    }

    private fun isDeviceAdminEnabled(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(this, UninstallProtectionReceiver::class.java)
        return dpm.isAdminActive(compName)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startService(Intent(this, DnsVpnService::class.java))
            btnVpn.text = "2. SafeSearch & Adult DNS Filter: ACTIVE ✅"
        } else if (requestCode == 100) {
            Toast.makeText(this, "VPN Permission Denied", Toast.LENGTH_SHORT).show()
        }
        updateButtonStates()
    }
}
