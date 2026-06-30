//
//  AudioPopupView.swift
//  telemetryphone
//
//  Created by Marino Vargas Zapata on 09/06/2026.
//
import AVFoundation
import SwiftUI

struct AudioPopupView: View {
    let pin: String
    var onFinish: () -> Void
    
    @StateObject private var audioManager = AudioRecorderManager()
    @State private var phase: PopupPhase = .recording
    @AppStorage("encuesta_completada_num") private var encuestasCompletadas: String = ""
    
    private let upmUsername = "simca.insia"
    private let upmPassword = "fpLiD-HFKL2-NiBtJ-NDpMc-fLPrx"
    private let webDavBaseUrl = "https://drive.upm.es/remote.php/dav/files/simca.insia"
    
    enum PopupPhase {
        case recording, reviewing
    }
    
    var body: some View {
        VStack(spacing: 25) {
            if phase == .recording {
                            // --- FASE 1: GRABAR (MANTENER PULSADO) ---
                            
                            // Texto de instrucciones dinámico
                            Text(audioManager.isRecording ? "Grabando... \nSuelta para parar" : "¿Quiere dejarnos alguna opinión?\nManténga pulsado el botón")
                            .font(.title.bold())
                                .foregroundColor(audioManager.isRecording ? .red : .primary)
                                .multilineTextAlignment(.center)
                            
                            Text("Tiempo máximo: \(audioManager.timeRemaining)segundos")
                                .font(.headline)
                            
                            // Sustituimos el Button por una Image con Gesture
                            Image(systemName: "mic.circle.fill")
                                .resizable()
                                .frame(width: 150, height: 150)
                                .foregroundColor(audioManager.isRecording ? .red : .blue)
                                // Efecto visual: el botón crece un poco mientras grabas
                                .scaleEffect(audioManager.isRecording ? 1.15 : 1.0)
                                .animation(.spring(response: 0.3, dampingFraction: 0.6), value: audioManager.isRecording)
                                
                                // La magia del Push-to-Talk
                                .gesture(
                                    DragGesture(minimumDistance: 0)
                                        .onChanged { _ in
                                            // El dedo acaba de tocar la pantalla (ACTION_DOWN)
                                            if !audioManager.isRecording {
                                                // Añadimos una pequeña vibración háptica de confirmación
                                                let impactMed = UIImpactFeedbackGenerator(style: .medium)
                                                impactMed.impactOccurred()
                                                
                                                audioManager.startRecording(pin: pin)
                                            }
                                        }
                                        .onEnded { _ in
                                            // El dedo se ha levantado (ACTION_UP)
                                            if audioManager.isRecording {
                                                audioManager.stopRecording()
                                                // Pasamos automáticamente a la fase de revisión
                                                phase = .reviewing
                                            }
                                        }
                                )
                            
                            Button("Cerrar") {
                                if audioManager.isRecording { audioManager.stopRecording() }
                                marcarComoCompletadoYSalir()
                            }
                            .buttonStyle(.bordered)
                            .tint(.gray)
                            .font(.title2)
                            .padding(.top, 20)
                            
                        } else {
                            
                // --- FASE 2: REVISIÓN ---
                Text("Revisa tu grabación")
                                .font(.title.bold())
                
                Button(action: {
                    audioManager.isPlaying ? audioManager.stopPlayback() : audioManager.playAudio()
                }) {
                    Image(systemName: audioManager.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .resizable()
                        .frame(width: 100, height: 100)
                }
                
                HStack(spacing: 20) {
                    Button("Reintentar") {
                        audioManager.stopPlayback()
                        audioManager.deleteAudio()
                        phase = .recording
                    }
                    .buttonStyle(.bordered)
                    .tint(.orange)
                    .font(.title2)
                    
                    Button("Enviar") {
                        audioManager.stopPlayback()
                        enviarAudio()
                        marcarComoCompletadoYSalir()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.green)
                    .font(.title2)
                }
            }
        }
        .padding()
    }
    
    private func enviarAudio() {
            guard let fileURL = audioManager.recordedAudioURL else {
                marcarComoCompletadoYSalir()
                return
            }
            
            // Lanzamos la subida en segundo plano
            Task {
                let success = await uploadAudioToUPMDrive(fileUrl: fileURL)
                
                // Volvemos al hilo principal para actualizar la interfaz
                await MainActor.run {
                    if success {
                        print("Audio subido correctamente a UPM Drive")
                        audioManager.deleteAudio() // Borramos el local si se ha subido bien
                    } else {
                        print("Error al subir el audio. Se ha guardado localmente.")
                        // Nota: Si quieres que se reintente subir luego, puedes dejar el archivo
                        // y hacer que el TelemetryViewModel también busque archivos .m4a en su función uploadPendingFiles()
                    }
                    
                    marcarComoCompletadoYSalir()
                }
            }
        }
        
        private func uploadAudioToUPMDrive(fileUrl: URL) async -> Bool {
            let fullUrlString = "\(webDavBaseUrl)/datos_app/\(fileUrl.lastPathComponent)"
            guard let url = URL(string: fullUrlString) else { return false }
            
            var request = URLRequest(url: url)
            request.httpMethod = "PUT"
            
            let loginString = "\(upmUsername):\(upmPassword)"
            guard let loginData = loginString.data(using: .utf8) else { return false }
            let base64LoginString = loginData.base64EncodedString()
            
            request.setValue("Basic \(base64LoginString)", forHTTPHeaderField: "Authorization")
            // Indicamos específicamente que es un archivo de audio
            request.setValue("audio/mp4", forHTTPHeaderField: "Content-Type")
            
            do {
                let fileData = try Data(contentsOf: fileUrl)
                let (_, response) = try await URLSession.shared.upload(for: request, from: fileData)
                if let httpResponse = response as? HTTPURLResponse {
                    return (200...299).contains(httpResponse.statusCode)
                }
            } catch {
                print("Error en subida de audio: \(error)")
            }
            return false
        }
    
    private func marcarComoCompletadoYSalir() {
        // Equivalente a tu lógica de SharedPreferences
        if !encuestasCompletadas.contains(pin) {
            encuestasCompletadas += "[\(pin)]"
        }
        onFinish()
    }
}
