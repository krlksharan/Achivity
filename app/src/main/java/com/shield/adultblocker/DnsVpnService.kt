package com.shield.adultblocker

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.widget.Toast

class DnsVpnService : VpnService() {

    companion object {
        var isRunning = false
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (vpnInterface == null) {
                val builder = Builder()
                    .setSession("Shield SafeSearch & Adult DNS")
                    .addAddress("10.0.0.2", 32)
                    // Set DNS servers WITHOUT trapping routes in the local tunnel
                    // so normal websites (YouTube, Google, etc.) load at full speed!
                    .addDnsServer("1.1.1.3")
                    .addDnsServer("1.0.0.3")
                    .setBlocking(false)

                vpnInterface = builder.establish()
                isRunning = vpnInterface != null
            }
            Toast.makeText(
                applicationContext,
                "SafeSearch & Adult DNS Filter Activated!",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        isRunning = false
        super.onDestroy()
    }
}
