package com.shield.adultblocker

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val appCtx = context.applicationContext
            HostsRepository.initialize(appCtx)

            // Only re-apply Uninstall Protection on boot if the user manually enabled it previously
            val dpm = appCtx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(appCtx, UninstallProtectionReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                UninstallProtectionReceiver.applyEnterprisePolicies(appCtx)
            }
        }
    }
}
