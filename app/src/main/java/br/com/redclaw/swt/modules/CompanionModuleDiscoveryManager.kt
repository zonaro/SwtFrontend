package br.com.redclaw.swt.modules

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import br.com.redclaw.swt.module.sdk.ModuleContract
import java.security.MessageDigest

/** Read-only discovery of installed companion APK modules. Binding is added in the next slice. */
class CompanionModuleDiscoveryManager(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val hostPackageName = context.applicationContext.packageName

    data class DiscoveredModule(
        val id: String,
        val apiVersion: Int,
        val componentName: ComponentName,
        val packageName: String,
    )

    fun discover(): List<DiscoveredModule> {
        val intent = Intent(ModuleContract.ACTION_MODULE_SERVICE)
        val services = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)
        }

        return services.mapNotNull { resolved ->
            val service = resolved.serviceInfo ?: return@mapNotNull null
            if (!service.exported || service.permission != ModuleContract.PERMISSION_BIND_MODULE) {
                return@mapNotNull null
            }
            val metadata = service.metaData ?: return@mapNotNull null
            val id = metadata.getString(ModuleContract.METADATA_MODULE_ID)
                ?.takeIf { it.matches(MODULE_ID_PATTERN) }
                ?: return@mapNotNull null
            val apiVersion = metadata.getInt(ModuleContract.METADATA_API_VERSION, 0)
            if (apiVersion != ModuleContract.API_VERSION) return@mapNotNull null
            if (!hasHostSignature(service.packageName)) return@mapNotNull null
            DiscoveredModule(
                id = id,
                apiVersion = apiVersion,
                componentName = ComponentName(service.packageName, service.name),
                packageName = service.packageName,
            )
        }.distinctBy { it.id }.sortedBy { it.id }
    }

    private fun hasHostSignature(candidatePackage: String): Boolean {
        val host = signingCertificateDigests(hostPackageName)
        return host.isNotEmpty() && host == signingCertificateDigests(candidatePackage)
    }

    private fun signingCertificateDigests(packageName: String): Set<String> = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_SIGNING_CERTIFICATES.toLong(),
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
            }
            val signingInfo = info.signingInfo ?: return@runCatching emptySet()
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signatures.orEmpty().mapTo(linkedSetOf()) { sha256(it.toByteArray()) }
        } else {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures.orEmpty().mapTo(linkedSetOf()) { sha256(it.toByteArray()) }
        }
    }.getOrDefault(emptySet())

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }

    private companion object {
        val MODULE_ID_PATTERN = Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")
    }
}
