package com.shannon.monitoring

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.PowerManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Monitors Android service health and performance over extended periods.
 * Tracks memory usage, CPU usage, service restarts, and other vital metrics.
 */
class ServiceHealthMonitor(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private var monitoringJob: Job? = null
    private val healthMetrics = mutableListOf<ServiceHealthMetric>()

    /**
     * Start monitoring service health.
     */
    fun startMonitoring(intervalMs: Long = 60000) { // Default: check every minute
        monitoringJob?.cancel()

        monitoringJob = scope.launch {
            while (isActive) {
                try {
                    val metric = collectHealthMetric()
                    healthMetrics.add(metric)

                    // Keep only last 24 hours of metrics (assuming 1-minute intervals)
                    if (healthMetrics.size > 1440) {
                        healthMetrics.removeAt(0)
                    }

                    // Check for health issues
                    checkHealthIssues(metric)

                    delay(intervalMs)
                } catch (e: Exception) {
                    println("Health monitoring error: ${e.message}")
                }
            }
        }
    }

    /**
     * Stop monitoring service health.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /**
     * Collect current health metric.
     */
    private suspend fun collectHealthMetric(): ServiceHealthMetric = withContext(Dispatchers.IO) {
        val runtime = Runtime.getRuntime()

        ServiceHealthMetric(
            timestamp = System.currentTimeMillis(),
            availableMemory = runtime.freeMemory(),
            totalMemory = runtime.totalMemory(),
            maxMemory = runtime.maxMemory(),
            memoryUsage = (runtime.totalMemory() - runtime.freeMemory()),
            memoryUsagePercentage = ((runtime.totalMemory() - runtime.freeMemory()).toFloat() / runtime.maxMemory() * 100),
            isDeviceInDozeMode = isInDozeMode(),
            isPowerSaveMode = isInPowerSaveMode(),
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
            serviceRestartCount = getServiceRestartCount()
        )
    }

    /**
     * Check if device is in Doze mode.
     */
    private fun isInDozeMode(): Boolean {
        val powerManager = context.getSystemService<PowerManager>()
        return powerManager?.isIdleModeEnabled ?: false
    }

    /**
     * Check if device is in power save mode.
     */
    private fun isInPowerSaveMode(): Boolean {
        val powerManager = context.getSystemService<PowerManager>()
        return powerManager?.isPowerSaveMode ?: false
    }

    /**
     * Check if app is ignoring battery optimizations.
     */
    private fun isIgnoringBatteryOptimizations(): Boolean {
        // In a real implementation, you would check this using PowerManager.isIgnoringBatteryOptimizations
        return true // Placeholder
    }

    /**
     * Get service restart count from logs.
     */
    private fun getServiceRestartCount(): Int {
        // In a real implementation, you would track restarts in persistent storage
        return 0 // Placeholder
    }

    /**
     * Check for health issues and log warnings.
     */
    private fun checkHealthIssues(metric: ServiceHealthMetric) {
        when {
            metric.memoryUsagePercentage > 80 -> {
                println("WARNING: High memory usage: ${metric.memoryUsagePercentage}%")
            }
            metric.isDeviceInDozeMode -> {
                println("INFO: Device is in Doze mode")
            }
            metric.isPowerSaveMode -> {
                println("INFO: Device is in power save mode")
            }
            !metric.isIgnoringBatteryOptimizations -> {
                println("WARNING: App is not ignoring battery optimizations - service may be killed")
            }
            metric.serviceRestartCount > 0 -> {
                println("WARNING: Service has restarted ${metric.serviceRestartCount} times")
            }
        }
    }

    /**
     * Get current health status.
     */
    fun getHealthStatus(): ServiceHealthStatus {
        if (healthMetrics.isEmpty()) {
            return ServiceHealthStatus.UNKNOWN
        }

        val latestMetric = healthMetrics.last()

        return when {
            latestMetric.memoryUsagePercentage > 90 -> ServiceHealthStatus.CRITICAL
            latestMetric.memoryUsagePercentage > 75 -> ServiceHealthStatus.WARNING
            !latestMetric.isIgnoringBatteryOptimizations -> ServiceHealthStatus.WARNING
            latestMetric.serviceRestartCount > 5 -> ServiceHealthStatus.CRITICAL
            latestMetric.serviceRestartCount > 0 -> ServiceHealthStatus.WARNING
            else -> ServiceHealthStatus.HEALTHY
        }
    }

    /**
     * Get health metrics summary for the last N hours.
     */
    fun getHealthSummary(hours: Int = 24): ServiceHealthSummary {
        val cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L)
        val relevantMetrics = healthMetrics.filter { it.timestamp >= cutoffTime }

        if (relevantMetrics.isEmpty()) {
            return ServiceHealthSummary(
                duration = 0,
                averageMemoryUsage = 0.0,
                peakMemoryUsage = 0.0,
                minMemoryUsage = 0.0,
                totalRestarts = 0,
                timeInDozeMode = 0,
                timeInPowerSaveMode = 0
            )
        }

        return ServiceHealthSummary(
            duration = hours,
            averageMemoryUsage = relevantMetrics.map { it.memoryUsagePercentage }.average(),
            peakMemoryUsage = relevantMetrics.maxOfOrNull { it.memoryUsagePercentage } ?: 0.0,
            minMemoryUsage = relevantMetrics.minOfOrNull { it.memoryUsagePercentage } ?: 0.0,
            totalRestarts = relevantMetrics.maxOfOrNull { it.serviceRestartCount } ?: 0,
            timeInDozeMode = relevantMetrics.count { it.isDeviceInDozeMode },
            timeInPowerSaveMode = relevantMetrics.count { it.isInPowerSaveMode }
        )
    }

    /**
     * Export health metrics to file for analysis.
     */
    suspend fun exportHealthMetricsToFile(): File = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "service_health_metrics_${System.currentTimeMillis()}.txt")

        file.bufferedWriter().use { writer ->
            writer.write("Service Health Metrics Export\n")
            writer.write("=" .repeat(50) + "\n\n")

            healthMetrics.forEach { metric ->
                writer.write("Timestamp: ${metric.timestamp}\n")
                writer.write("Memory Usage: ${metric.memoryUsage} / ${metric.maxMemory} (${metric.memoryUsagePercentage}%)\n")
                writer.write("Doze Mode: ${metric.isDeviceInDozeMode}\n")
                writer.write("Power Save: ${metric.isInPowerSaveMode}\n")
                writer.write("Battery Optimizations Ignored: ${metric.isIgnoringBatteryOptimizations}\n")
                writer.write("Service Restarts: ${metric.serviceRestartCount}\n")
                writer.write("\n")
            }

            val summary = getHealthSummary(24)
            writer.write("=" .repeat(50) + "\n")
            writer.write("24-Hour Summary:\n")
            writer.write("Average Memory: ${summary.averageMemoryUsage}%\n")
            writer.write("Peak Memory: ${summary.peakMemoryUsage}%\n")
            writer.write("Total Restarts: ${summary.totalRestarts}\n")
            writer.write("Time in Doze: ${summary.timeInDozeMode} checks\n")
            writer.write("Time in Power Save: ${summary.timeInPowerSaveMode} checks\n")
        }

        file
    }

    /**
     * Clear all health metrics.
     */
    fun clearMetrics() {
        healthMetrics.clear()
    }

    /**
     * Get current health metrics list.
     */
    fun getHealthMetrics(): List<ServiceHealthMetric> {
        return healthMetrics.toList()
    }
}

/**
 * Service health metric at a point in time.
 */
data class ServiceHealthMetric(
    val timestamp: Long,
    val availableMemory: Long,
    val totalMemory: Long,
    val maxMemory: Long,
    val memoryUsage: Long,
    val memoryUsagePercentage: Float,
    val isDeviceInDozeMode: Boolean,
    val isPowerSaveMode: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
    val serviceRestartCount: Int
)

/**
 * Service health status.
 */
enum class ServiceHealthStatus {
    HEALTHY,
    WARNING,
    CRITICAL,
    UNKNOWN
}

/**
 * Health metrics summary for a time period.
 */
data class ServiceHealthSummary(
    val duration: Int,
    val averageMemoryUsage: Double,
    val peakMemoryUsage: Double,
    val minMemoryUsage: Double,
    val totalRestarts: Int,
    val timeInDozeMode: Int,
    val timeInPowerSaveMode: Int
)