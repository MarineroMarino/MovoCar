import Foundation
import _MapKit_SwiftUI
import CoreLocation
import CoreMotion
import UIKit
internal import Combine
import MapKit

class TelemetryViewModel: NSObject, ObservableObject, CLLocationManagerDelegate {
    
    // --- CONFIGURACIÓN UPM DRIVE (WEBDAV) ---
    private let upmUsername = "simca.insia"
    private let upmPassword = "fpLiD-HFKL2-NiBtJ-NDpMc-fLPrx"
    private let webDavBaseUrl = "https://drive.upm.es/remote.php/dav/files/simca.insia"
    
    // Identificador de sesión (PIN de 4 dígitos)
    @Published var pin: String = ""
    @Published var isCollecting = false
    @Published var statusText: String = "Listos para comenzar"
    
    @Published var cameraPosition: MapCameraPosition = .userLocation(fallback: .automatic)
    
    private var collectedRecords: [Telemetry] = []
    private var collectionTask: Task<Void, Never>?
    
    
    
    // Managers de iOS
    private let locationManager = CLLocationManager()
    private let motionManager = CMMotionManager()
    private let altimeter = CMAltimeter()
    
    // Variables para almacenar los últimos datos leídos
    private var currentLocation: CLLocation?
    private var currentPressure: Double? // en kPa
    
    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        
        // Iniciar subida de archivos pendientes
        Task { await uploadPendingFiles() }
    }
    
    func toggleCollection() {
        if isCollecting {
            stopCollectionAndUpload()
        } else {
            startCollectionIfValid()
        }
    }
    
    private func startCollectionIfValid() {
        // Comprobamos que sea un PIN numérico válido
        guard isValidPin(pin) else {
            
            return
        }
        
        // Solicitar permisos de ubicación
        locationManager.requestAlwaysAuthorization()
        locationManager.startUpdatingLocation()
        
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.showsBackgroundLocationIndicator = true
        
        // Iniciar sensores
        if motionManager.isAccelerometerAvailable { motionManager.startAccelerometerUpdates() }
        if motionManager.isGyroAvailable { motionManager.startGyroUpdates() }
        if motionManager.isMagnetometerAvailable { motionManager.startMagnetometerUpdates() }
        if CMAltimeter.isRelativeAltitudeAvailable() {
            altimeter.startRelativeAltitudeUpdates(to: .main) { data, _ in
                self.currentPressure = data?.pressure.doubleValue
            }
        }
        
        collectedRecords.removeAll()
        isCollecting = true
        statusText = ""
        
        // Bucle de recolección (100ms)
        collectionTask = Task {
            while !Task.isCancelled {
                let record = collectSnapshot(sessionId: pin)
                collectedRecords.append(record)
                
                await MainActor.run {
                }
                
                // Esperar 100ms (100_000_000 nanosegundos)
                try? await Task.sleep(nanoseconds: 200_000_000)
            }
        }
    }
    
    private func stopCollectionAndUpload() {
        isCollecting = false
        collectionTask?.cancel()
        
        // Detener sensores
        locationManager.stopUpdatingLocation()
        motionManager.stopAccelerometerUpdates()
        motionManager.stopGyroUpdates()
        motionManager.stopMagnetometerUpdates()
        altimeter.stopRelativeAltitudeUpdates()
        
        guard !collectedRecords.isEmpty else {
            
            return
        }
        
        
        
        let batch = TelemetryBatch(
            generatedAt: Int64(Date().timeIntervalSince1970 * 1000),
            count: collectedRecords.count,
            records: collectedRecords
        )
        
        Task {
            if let fileUrl = saveBatchCsv(batch: batch, sessionId: pin) {
                
                let success = await uploadFileToUPMDrive(fileUrl: fileUrl)
                
                await MainActor.run {
                    if success {
                        
                        try? FileManager.default.removeItem(at: fileUrl)
                        Task { await uploadPendingFiles() }
                    } else {
                        
                    }
                }
            }
            collectedRecords.removeAll()
        }
    }
    
    // MARK: - Recolección de Datos
    private func collectSnapshot(sessionId: String) -> Telemetry {
        let device = DeviceInfo(
            manufacturer: "Apple",
            model: UIDevice.current.model,
            systemName: UIDevice.current.systemName,
            systemVersion: UIDevice.current.systemVersion
        )
        
        var readings = [String: SensorReading]()
        
        if let acc = motionManager.accelerometerData {
            readings["Accelerometer"] = SensorReading(values: [acc.acceleration.x, acc.acceleration.y, acc.acceleration.z], unit: "g")
        }
        if let gyro = motionManager.gyroData {
            readings["Gyroscope"] = SensorReading(values: [gyro.rotationRate.x, gyro.rotationRate.y, gyro.rotationRate.z], unit: "rad/s")
        }
        if let mag = motionManager.magnetometerData {
            readings["Magnetometer"] = SensorReading(values: [mag.magneticField.x, mag.magneticField.y, mag.magneticField.z], unit: "µT")
        }
        if let pressure = currentPressure {
            readings["Pressure"] = SensorReading(values: [pressure * 10], unit: "hPa") // Convertir kPa a hPa
        }
        
        var locInfo: LocationInfo? = nil
        if let loc = currentLocation {
            locInfo = LocationInfo(
                latitude: loc.coordinate.latitude,
                longitude: loc.coordinate.longitude,
                accuracyMeters: loc.horizontalAccuracy,
                altitudeMeters: loc.altitude,
                speedMps: loc.speed >= 0 ? loc.speed : nil
            )
        }
        
        return Telemetry(
            sessionId: sessionId,
            timestamp: Int64(Date().timeIntervalSince1970 * 1000),
            device: device,
            location: locInfo,
            sensors: SensorsInfo(readings: readings)
        )
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        currentLocation = locations.last
    }
    
    // MARK: - Archivos CSV y Red (WebDAV)
    private func saveBatchCsv(batch: TelemetryBatch, sessionId: String) -> URL? {
        // Al ser un PIN numérico, no necesitamos limpieza de caracteres complejos,
        // pero lo mantenemos seguro frente a espacios
        let safePin = sessionId.trimmingCharacters(in: .whitespacesAndNewlines)
        let filename = "telemetry_\(safePin)_\(batch.generatedAt).csv"
        
        let docsUrl = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let fileUrl = docsUrl.appendingPathComponent(filename)
        
        do {
            let encoder = JSONEncoder()
            let jsonData = try encoder.encode(batch.records)
            let csvString = try convertirJsonACsv(jsonData: jsonData)
            try csvString.write(to: fileUrl, atomically: true, encoding: .utf8)
            return fileUrl
        } catch {
            print("Error guardando CSV: \(error)")
            return nil
        }
    }
    
    private func convertirJsonACsv(jsonData: Data) throws -> String {
        guard let jsonArray = try JSONSerialization.jsonObject(with: jsonData, options: []) as? [[String: Any]],
              let primerObjeto = jsonArray.first else {
            return ""
        }
        
        var csvBuilder = ""
        
        // Cabeceras
        let cabeceras = Array(primerObjeto.keys).sorted()
        csvBuilder.append(cabeceras.joined(separator: ",") + "\n")
        
        // Fila por fila
        for jsonObject in jsonArray {
            var fila: [String] = []
            
            for clave in cabeceras {
                if let valor = jsonObject[clave], !(valor is NSNull) {
                    var stringValue = ""
                    
                    if let dictOrArray = valor as? [String: Any] {
                        let nestedData = try JSONSerialization.data(withJSONObject: dictOrArray, options: [])
                        stringValue = String(data: nestedData, encoding: .utf8) ?? ""
                    } else if let arr = valor as? [Any] {
                        let nestedData = try JSONSerialization.data(withJSONObject: arr, options: [])
                        stringValue = String(data: nestedData, encoding: .utf8) ?? ""
                    } else {
                        stringValue = "\(valor)"
                    }
                    
                    stringValue = stringValue.replacingOccurrences(of: "\"", with: "\"\"")
                    fila.append("\"\(stringValue)\"")
                } else {
                    fila.append("\"\"")
                }
            }
            csvBuilder.append(fila.joined(separator: ",") + "\n")
        }
        
        return csvBuilder
    }
    
    private func uploadFileToUPMDrive(fileUrl: URL) async -> Bool {
        let fullUrlString = "\(webDavBaseUrl)/datos_app/\(fileUrl.lastPathComponent)"
        guard let url = URL(string: fullUrlString) else { return false }
        
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        
        let loginString = "\(upmUsername):\(upmPassword)"
        guard let loginData = loginString.data(using: .utf8) else { return false }
        let base64LoginString = loginData.base64EncodedString()
        
        request.setValue("Basic \(base64LoginString)", forHTTPHeaderField: "Authorization")
        request.setValue("text/csv", forHTTPHeaderField: "Content-Type")
        
        do {
            let fileData = try Data(contentsOf: fileUrl)
            let (_, response) = try await URLSession.shared.upload(for: request, from: fileData)
            if let httpResponse = response as? HTTPURLResponse {
                return (200...299).contains(httpResponse.statusCode)
            }
        } catch {
            print("Error en subida: \(error)")
        }
        return false
    }
    
    private func uploadPendingFiles() async {
        let docsUrl = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        do {
            let files = try FileManager.default.contentsOfDirectory(at: docsUrl, includingPropertiesForKeys: nil)
            // Dentro de TelemetryViewModel.swift -> uploadPendingFiles()

            for file in files where file.lastPathComponent.hasPrefix("telemetry_") {
                // Si es CSV o M4A, intentamos subirlo
                if file.pathExtension == "csv" || file.pathExtension == "m4a" {
                    let success = await uploadFileToUPMDrive(fileUrl: file)
                    if success {
                        try? FileManager.default.removeItem(at: file)
                    }
                }
            }
        } catch {
            print("Error leyendo archivos pendientes: \(error)")
        }
    }
    
    // Nueva validación para el PIN
    private func isValidPin(_ pin: String) -> Bool {
        // Valida que el String contenga exactamente 4 dígitos del 0 al 9
        let pinRegEx = "^[0-9]{4}$"
        let pinPred = NSPredicate(format:"SELF MATCHES %@", pinRegEx)
        return pinPred.evaluate(with: pin)
    }
}
