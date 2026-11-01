package com.shield.adultblocker

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.widget.Toast

class UninstallProtectionReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        applyEnterprisePolicies(context)
        Toast.makeText(context, "Uninstall Protection Enabled ✅", Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun applyEnterprisePolicies(context: Context) {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, UninstallProtectionReceiver::class.java)

                if (dpm.isDeviceOwnerApp(context.packageName)) {
                    dpm.setUninstallBlocked(adminComponent, context.packageName, true)
                    dpm.setPermittedAccessibilityServices(
                        adminComponent,
                        listOf(context.packageName)
                    )
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
