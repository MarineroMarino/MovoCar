package com.example.telemetryphone

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class TelemetryBatch(
    val generatedAt: Long,
    val count: Int,
    val records: List<Telemetry>
)
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Telemetry(
    val sessionId: String,
    val timestamp: Long,
    val device: DeviceInfo,
    val location: LocationInfo? = null,
    val sensors: SensorsInfo? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val product: String,
    val sdkInt: Int,
    val androidRelease: String
)


@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SensorsInfo(
    val readings: Map<String, SensorReading> = emptyMap()
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SensorReading(
    val values: List<Float>,
    val vendor: String? = null,
    val version: Int? = null,
    val unit: String? = null,
    val accuracy: Int? = null,
    val eventTimestampNs: Long? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LocationInfo(
    val provider: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val speedMps: Float? = null
)