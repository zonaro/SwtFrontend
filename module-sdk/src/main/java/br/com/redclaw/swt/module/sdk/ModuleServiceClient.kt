package br.com.redclaw.swt.module.sdk

import android.os.IBinder
import android.os.Parcel

/** Synchronous API-v1 Binder call. Invoke from a background thread. */
object ModuleServiceClient {
    fun getDescriptor(binder: IBinder): ModuleDescriptor? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(ModuleContract.BINDER_DESCRIPTOR)
            if (!binder.transact(ModuleContract.TRANSACTION_GET_DESCRIPTOR, data, reply, 0)) {
                return null
            }
            reply.readException()
            ModuleDescriptor.fromBundle(reply.readBundle(ModuleDescriptor::class.java.classLoader) ?: return null)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
