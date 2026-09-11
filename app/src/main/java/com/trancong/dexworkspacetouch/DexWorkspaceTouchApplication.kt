package com.trancong.dexworkspacetouch

import android.app.Application
import androidx.room.Room
import com.trancong.dexworkspacetouch.workspace.persistence.repository.RoomWorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.persistence.room.DexWorkspaceDatabase
import com.trancong.dexworkspacetouch.workspace.persistence.room.MIGRATION_1_2
import com.trancong.dexworkspacetouch.workspace.persistence.serialization.DeterministicWorkspaceCanvasJsonSerializer
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.PackageManagerAppIconLoader
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader
import com.trancong.dexworkspacetouch.feature.car.overlay.CarFloatingDockCoordinator
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutPreferences
import com.trancong.dexworkspacetouch.feature.car.createCarWorkspaceShortcutPreferences
import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.trancong.dexworkspacetouch.license.activation.AndroidApplicationIdentityProvider
import com.trancong.dexworkspacetouch.license.activation.DefaultLicenseRepository
import com.trancong.dexworkspacetouch.license.activation.LicenseRepository
import com.trancong.dexworkspacetouch.license.activation.LicenseTokenVerifier
import com.trancong.dexworkspacetouch.license.activation.OkHttpLicenseApiClient
import com.trancong.dexworkspacetouch.license.activation.SharedPreferencesLicenseTokenStore
import com.trancong.dexworkspacetouch.license.activation.TrustedLicenseSigningKeys
import com.trancong.dexworkspacetouch.license.domain.LicenseTimeProvider
import com.trancong.dexworkspacetouch.license.infrastructure.identity.AndroidDeviceIdentityProvider
import com.trancong.dexworkspacetouch.update.AppUpdateRepository
import com.trancong.dexworkspacetouch.update.UpdateManifestClient
import okhttp3.OkHttpClient

class DexWorkspaceTouchApplication : Application() {
    lateinit var workspaceRepository: WorkspaceRepository
        private set
    lateinit var appIconLoader: AppIconLoader
        private set
    lateinit var carFloatingDockCoordinator: CarFloatingDockCoordinator
        private set
    lateinit var carWorkspaceShortcutPreferences: CarWorkspaceShortcutPreferences
        private set
    lateinit var licenseRepository: LicenseRepository
        private set
    lateinit var appUpdateRepository: AppUpdateRepository
        private set
    val licenseTimeProvider = LicenseTimeProvider(System::currentTimeMillis)
    val carWorkflowExecutionArbiter = CarWorkflowExecutionArbiter()
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        appIconLoader = PackageManagerAppIconLoader.create(applicationContext)
        val database = Room.databaseBuilder(
            applicationContext,
            DexWorkspaceDatabase::class.java,
            DexWorkspaceDatabase.DATABASE_NAME,
        ).addMigrations(MIGRATION_1_2).build()
        workspaceRepository = RoomWorkspaceRepository(
            dao = database.workspaceDao(),
            serializer = DeterministicWorkspaceCanvasJsonSerializer(),
        )
        carWorkspaceShortcutPreferences = createCarWorkspaceShortcutPreferences(applicationContext)
        carFloatingDockCoordinator = CarFloatingDockCoordinator.create(
            applicationContext,
            processScope,
            workspaceRepository,
            carWorkspaceShortcutPreferences,
            carWorkflowExecutionArbiter,
        )
        val trustedKeys = if (BuildConfig.DEBUG && BuildConfig.LICENSE_TRUSTED_PUBLIC_KEYS_JSON == "[]") {
            TrustedLicenseSigningKeys.emptyForDebug()
        } else {
            TrustedLicenseSigningKeys.fromRegistryJson(BuildConfig.LICENSE_TRUSTED_PUBLIC_KEYS_JSON)
        }
        val deviceKeyManager = com.trancong.dexworkspacetouch.license.infrastructure.identity.AndroidDeviceKeyManager.create(applicationContext)
        appUpdateRepository = UpdateManifestClient(
            client = OkHttpClient(),
            manifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
            currentVersionCode = BuildConfig.VERSION_CODE,
            expectedApplicationId = BuildConfig.APPLICATION_ID,
            expectedSignerSha256 = BuildConfig.APK_SIGNING_CERTIFICATE_SHA256,
            allowCleartextManifestForDebug = BuildConfig.DEBUG,
        )
        licenseRepository = DefaultLicenseRepository(
            deviceIdentityProvider = AndroidDeviceIdentityProvider.create(applicationContext),
            deviceKeyManager = deviceKeyManager,
            applicationIdentityProvider = AndroidApplicationIdentityProvider.create(applicationContext),
            apiClient = OkHttpLicenseApiClient(
                baseUrl = BuildConfig.LICENSE_API_BASE_URL,
                allowCleartext = BuildConfig.DEBUG,
            ),
            tokenVerifier = LicenseTokenVerifier(trustedKeys, licenseTimeProvider),
            tokenStore = SharedPreferencesLicenseTokenStore.create(applicationContext),
            timeProvider = licenseTimeProvider,
        )
    }
}
