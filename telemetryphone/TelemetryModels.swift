//
//  TelemetryModels.swift
//  telemetryphone
//
//  Created by Marino Vargas Zapata on 27/04/2026.
//

import Foundation

struct TelemetryBatch: Codable {
    let generatedAt: Int64
    let count: Int
    let records: [Telemetry]
}

struct Telemetry: Codable {
    let sessionId: String
    let timestamp: Int64
    let device: DeviceInfo
    let location: LocationInfo?
    let sensors: SensorsInfo?
}

struct DeviceInfo: Codable {
    let manufacturer: String
    let model: String
    let systemName: String
    let systemVersion: String
}

struct SensorsInfo: Codable {
    let readings: [String: SensorReading]
}

struct SensorReading: Codable {
    let values: [Double]
    let unit: String?
}

struct LocationInfo: Codable {
    let latitude: Double
    let longitude: Double
    let accuracyMeters: Double?
    let altitudeMeters: Double?
    let speedMps: Double?
}
