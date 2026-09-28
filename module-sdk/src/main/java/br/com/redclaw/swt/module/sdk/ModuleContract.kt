package br.com.redclaw.swt.module.sdk

/** Stable discovery and Binder protocol shared by the frontend and companion modules. */
object ModuleContract {
    const val ACTION_MODULE_SERVICE = "br.com.redclaw.swt.module.BIND"
    const val PERMISSION_BIND_MODULE = "br.com.redclaw.swt.permission.BIND_MODULE"

    const val METADATA_MODULE_ID = "br.com.redclaw.swt.module.ID"
    const val METADATA_API_VERSION = "br.com.redclaw.swt.module.API_VERSION"

    const val BINDER_DESCRIPTOR = "br.com.redclaw.swt.module.sdk.IModuleService"
    const val TRANSACTION_GET_DESCRIPTOR = android.os.IBinder.FIRST_CALL_TRANSACTION

    const val API_VERSION = 1
}
