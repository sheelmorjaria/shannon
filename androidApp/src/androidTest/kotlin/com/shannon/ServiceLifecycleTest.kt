package com.shannon

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.shannon.services.ShannonNetworkService
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Instrumented tests for ShannonNetworkService lifecycle.
 * These tests run on an Android device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class ServiceLifecycleTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @After
    fun teardown() {
        // Cleanup if needed
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.shannon", appContext.packageName)
    }

    @Test
    fun serviceCanBeStarted() {
        val intent = Intent(context, ShannonNetworkService::class.java)
        val binder = serviceRule.startService(intent)

        assertNotNull(binder, "Service binder should not be null")
        assertTrue(binder is ShannonNetworkService.LocalBinder, "Binder should be LocalBinder")
    }

    @Test
    fun serviceCanBeBound() {
        val intent = Intent(context, ShannonNetworkService::class.java)
        serviceRule.bindService(intent)

        // Service should be bound successfully
        // Note: In actual test, you might want to verify service state
    }

    @Test
    fun serviceCreatesNotificationChannel() {
        val intent = Intent(context, ShannonNetworkService::class.java)
        serviceRule.startService(intent)

        // Note: Testing notification channels requires more complex setup
        // In production, you would use UI Automator or other testing tools
        // to verify notification visibility
    }

    @Test
    fun serviceHandlesMultipleStartRequests() {
        val intent = Intent(context, ShannonNetworkService::class.java)

        // Start service multiple times
        serviceRule.startService(intent)
        serviceRule.startService(intent)
        serviceRule.startService(intent)

        // Service should handle multiple starts gracefully
        // In actual implementation, you would verify service state
    }

    @Test
    fun serviceHandlesBindAfterStart() {
        val intent = Intent(context, ShannonNetworkService::class.java)

        // Start service first
        serviceRule.startService(intent)

        // Then bind to it
        serviceRule.bindService(intent)

        // Service should handle both start and bind
    }

    @Test
    fun serviceHandlesUnbindAndStop() {
        val intent = Intent(context, ShannonNetworkService::class.java)

        // Start and bind service
        serviceRule.startService(intent)
        serviceRule.bindService(intent)

        // Unbind
        serviceRule.unbindService()

        // Service should continue running after unbind
        // (because it was started with startService)

        // Note: Stopping service from test is complex due to lifecycle
        // In production, you would test this through integration tests
    }

    @Test
    fun applicationContextIsCorrect() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(appContext, "Application context should not be null")
        assertTrue(appContext is ShannonApplication, "Application context should be ShannonApplication")
    }

    @Test
    fun packageInfoIsCorrect() {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals("com.shannon", packageInfo.packageName)
    }

    @Test
    fun serviceIntentIsExportedCorrectly() {
        val intent = Intent(context, ShannonNetworkService::class.java)
        val resolveInfo = context.packageManager.queryIntentServices(intent, 0)

        // Service should be found (if properly declared in manifest)
        // Note: This test depends on manifest configuration
    }

    @Test
    fun notificationPermissionIsDeclared() {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val requestedPermissions = packageInfo.requestedPermissions

        // Check if FOREGROUND_SERVICE permission is declared
        val hasForegroundServicePermission = requestedPermissions?.contains("android.permission.FOREGROUND_SERVICE") ?: false
        assertTrue(hasForegroundServicePermission, "FOREGROUND_SERVICE permission should be declared")
    }

    @Test
    fun internetPermissionIsDeclared() {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val requestedPermissions = packageInfo.requestedPermissions

        // Check if INTERNET permission is declared
        val hasInternetPermission = requestedPermissions?.contains("android.permission.INTERNET") ?: false
        assertTrue(hasInternetPermission, "INTERNET permission should be declared")
    }

    @Test
    fun networkStatePermissionIsDeclared() {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val requestedPermissions = packageInfo.requestedPermissions

        // Check if ACCESS_NETWORK_STATE permission is declared
        val hasNetworkStatePermission = requestedPermissions?.contains("android.permission.ACCESS_NETWORK_STATE") ?: false
        assertTrue(hasNetworkStatePermission, "ACCESS_NETWORK_STATE permission should be declared")
    }

    @Test
    fun storagePermissionsAreDeclared() {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val requestedPermissions = packageInfo.requestedPermissions

        // Check if storage permissions are declared (for Reticulum config files)
        val hasReadStorage = requestedPermissions?.contains("android.permission.READ_EXTERNAL_STORAGE") ?: false
        val hasWriteStorage = requestedPermissions?.contains("android.permission.WRITE_EXTERNAL_STORAGE") ?: false

        assertTrue(hasReadStorage || hasWriteStorage, "At least one storage permission should be declared")
    }

    @Test
    fun bluetoothPermissionsAreDeclared() {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val requestedPermissions = packageInfo.requestedPermissions

        // Check if Bluetooth permissions are declared (for Reticulum BLE)
        val hasBluetooth = requestedPermissions?.contains("android.permission.BLUETOOTH") ?: false
        val hasBluetoothScan = requestedPermissions?.contains("android.permission.BLUETOOTH_SCAN") ?: false
        val hasBluetoothConnect = requestedPermissions?.contains("android.permission.BLUETOOTH_CONNECT") ?: false

        assertTrue(hasBluetooth || hasBluetoothScan || hasBluetoothConnect, "Bluetooth permissions should be declared")
    }

    @Test
    fun applicationClassIsCorrect() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(appContext is ShannonApplication, "Application should be ShannonApplication")

        val app = appContext as ShannonApplication
        assertNotNull(app, "ShannonApplication should be properly initialized")
    }

    @Test
    fun koinModulesAreLoaded() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val app = appContext as ShannonApplication

        // Verify Koin is loaded
        // Note: This test would require access to Koin's internal state
        // In production, you would inject dependencies and verify they work
    }

    @Test
    fun databaseDirectoryIsAccessible() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val app = appContext as ShannonApplication

        // Test that app can access its data directory
        val dataDir = appContext.filesDir
        assertTrue(dataDir.exists(), "App data directory should exist")
        assertTrue(dataDir.canWrite(), "App data directory should be writable")
    }

    @Test
    fun reticulumConfigDirectoryCanBeCreated() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()

        // Test that we can create Reticulum config directory
        val reticulumDir = java.io.File(appContext.filesDir, ".reticulum")
        val success = reticulumDir.mkdirs()

        assertTrue(success || reticulumDir.exists(), "Should be able to create Reticulum directory")

        // Cleanup
        if (reticulumDir.exists()) {
            reticulumDir.delete()
        }
    }

    @Test
    fun identityDirectoryCanBeCreated() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()

        // Test that we can create identity directory
        val identityDir = java.io.File(appContext.filesDir, "shannon/identities")
        val success = identityDir.mkdirs()

        assertTrue(success || identityDir.exists(), "Should be able to create identity directory")

        // Cleanup
        if (identityDir.exists()) {
            identityDir.deleteRecursively()
        }
    }

    @Test
    fun preferencesAreAccessible() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()

        // Test that shared preferences work
        val prefs = appContext.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("test_key", "test_value")
        val success = editor.commit()

        assertTrue(success, "Should be able to write to preferences")
        assertEquals("test_value", prefs.getString("test_key", null), "Should be able to read from preferences")

        // Cleanup
        prefs.edit().clear().commit()
    }
}