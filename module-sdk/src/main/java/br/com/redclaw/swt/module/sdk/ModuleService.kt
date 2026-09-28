package br.com.redclaw.swt.module.sdk

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel

/**
 * Minimal cross-process entry point for companion APK modules.
 *
 * Subclasses expose only immutable discovery data in API v1. Feature calls can be added as new
 * transaction codes without changing the discovery contract.
 */
abstract class ModuleService : Service() {
    protected abstract val moduleDescriptor: ModuleDescriptor

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == ModuleContract.TRANSACTION_GET_DESCRIPTOR) {
                data.enforceInterface(ModuleContract.BINDER_DESCRIPTOR)
                reply?.writeNoException()
                reply?.writeBundle(moduleDescriptor.toBundle())
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }.apply {
        attachInterface(null, ModuleContract.BINDER_DESCRIPTOR)
    }

    final override fun onBind(intent: Intent?): IBinder? =
        binder.takeIf { intent?.action == ModuleContract.ACTION_MODULE_SERVICE }
}
