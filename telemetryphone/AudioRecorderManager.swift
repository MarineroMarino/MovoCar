//
//  AudioRecorderManager.swift
//  telemetryphone
//
//  Created by Marino Vargas Zapata on 09/06/2026.
//


import AVFoundation
import SwiftUI
internal import Combine

class AudioRecorderManager: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published var isRecording = false
    @Published var isPlaying = false
    @Published var timeRemaining = 60
    @Published var recordedAudioURL: URL?
    
    private var audioRecorder: AVAudioRecorder?
    private var audioPlayer: AVAudioPlayer?
    private var timer: Timer?
    
    func startRecording(pin: String) {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playAndRecord, mode: .default)
        try? session.setActive(true)
        
        let fileName = "telemetry_audio_\(pin)_\(Date().timeIntervalSince1970).m4a"
        let path = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 12000,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
        ]
        
        do {
            audioRecorder = try AVAudioRecorder(url: path, settings: settings)
            audioRecorder?.record()
            isRecording = true
            recordedAudioURL = path
            
            // Iniciar temporizador de 60 segundos
            timeRemaining = 60
            timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
                guard let self = self else { return }
                if self.timeRemaining > 0 {
                    self.timeRemaining -= 1
                } else {
                    self.stopRecording()
                    self.timeRemaining = 60
                }
            }
        } catch {
            print("Error al iniciar grabación: \(error.localizedDescription)")
        }
    }
    
    func stopRecording() {
        audioRecorder?.stop()
        isRecording = false
        self.timeRemaining = 60
        timer?.invalidate()
    }
    
    func playAudio() {
        guard let url = recordedAudioURL else { return }
        do {
            audioPlayer = try AVAudioPlayer(contentsOf: url)
            audioPlayer?.delegate = self
            audioPlayer?.play()
            isPlaying = true
        } catch {
            print("Error al reproducir: \(error.localizedDescription)")
        }
    }
    
    func stopPlayback() {
        audioPlayer?.stop()
        isPlaying = false
    }
    
    func deleteAudio() {
        if let url = recordedAudioURL {
            try? FileManager.default.removeItem(at: url)
            recordedAudioURL = nil
        }
    }
    
    // Delegado de AVAudioPlayer
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        isPlaying = false
    }
}
