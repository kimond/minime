package app.minime.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

data class AppEntry(
    /** Stable id: component + user serial, so work-profile copies are distinct. */
    val key: String,
    /** What we display (may be a user rename). */
    val label: String,
    val originalLabel: String,
    val component: ComponentName,
    val user: UserHandle,
    val isWorkProfile: Boolean,
)

class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    /** Emits the full list of launchable apps now, and again whenever packages change. */
    fun appsFlow(): Flow<List<AppEntry>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageAdded(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageChanged(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackagesAvailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
        }
        send(Unit)
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }
        .conflate()
        .map { loadApps() }
        .flowOn(Dispatchers.IO)

    private fun loadApps(): List<AppEntry> {
        val me = Process.myUserHandle()
        return userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user).mapNotNull { info ->
                val component = info.componentName
                if (component.packageName == context.packageName) return@mapNotNull null
                val label = info.label?.toString()?.trim().orEmpty().ifEmpty { component.packageName }
                AppEntry(
                    key = "${component.flattenToShortString()}|$serial",
                    label = label,
                    originalLabel = label,
                    component = component,
                    user = user,
                    isWorkProfile = user != me,
                )
            }
        }
    }

    fun launch(app: AppEntry): Boolean = runCatching {
        launcherApps.startMainActivity(app.component, app.user, null, null)
    }.isSuccess

    fun openAppInfo(app: AppEntry): Boolean = runCatching {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }.isSuccess

    fun uninstall(app: AppEntry): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.isSuccess
}
