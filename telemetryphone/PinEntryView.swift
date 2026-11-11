//
//  PinEntryView.swift
//  telemetryphone
//
//  Created by Marino Vargas Zapata on 15/10/2026.
//


import SwiftUI

struct PinEntryView: View {
    @State private var pin: [String] = ["", "", "", ""]
    @FocusState private var focusedField: Int?
    @State private var navigateToTelemetry = false
    @State private var finalPin = ""
    @State private var showWarningPopup = true

    var body: some View {
        NavigationStack {
            ZStack {
                
                
                Image("Background_Gemini")
                    .resizable() // Permite que la imagen cambie de tamaño
                    .scaledToFill() // Hace que llene todo el espacio sin deformarse
                    .ignoresSafeArea()
                
                VStack(spacing: 30) {
                    Text("Introduce tu PIN")
                        .font(.title)
                    
                    HStack(spacing: 15) {
                        ForEach(0..<4, id: \.self) { index in
                            TextField("", text: $pin[index])
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.center)
                                .frame(width: 50, height: 60)
                                .background(Color.gray.opacity(0.2))
                                .cornerRadius(10)
                                .focused($focusedField, equals: index)
                                .onChange(of: pin[index]) { newValue in
                                    if newValue.count > 1 { pin[index] = String(newValue.last!) }
                                    if !newValue.isEmpty {
                                        if index < 3 { focusedField = index + 1 }
                                        else {
                                            focusedField = nil
                                            checkAndNavigate()
                                        }
                                    }
                                }
                        }
                    }
                }
                .padding()
                .navigationDestination(isPresented: $navigateToTelemetry) {
                    PreparationView(pin: finalPin)
                }
                
                ContactButton()
                
                // --- POPUP DE AVISO INICIAL (5 SEGUNDOS) ---
                if showWarningPopup {
                    Color.black.opacity(0.45)
                        .ignoresSafeArea()
                        .onTapGesture {
                            dismissWarningPopup()
                        }
                    
                    WarningPopupView {
                        dismissWarningPopup()
                    }
                    .transition(.scale.combined(with: .opacity))
                    .zIndex(1)
                    .task {
                        try? await Task.sleep(for: .seconds(5))
                        guard !Task.isCancelled else { return }
                        dismissWarningPopup()
                    }
                }
            }
        }
    }
    
    private func dismissWarningPopup() {
        withAnimation(.spring()) {
            showWarningPopup = false
        }
    }
    
    private func checkAndNavigate() {
        let fullPin = pin.joined()
        if fullPin.count == 4 {
            finalPin = fullPin
            navigateToTelemetry = true
        }
    }
}

struct WarningPopupView: View {
    let onDismiss: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "exclamationmark.triangle.fill")
                .resizable()
                .scaledToFit()
                .frame(height: 75)
                .foregroundColor(.orange)
                .padding(.top, 10)
            
            Text("No utilices la aplicación mientras conduces. Inicia la grabación antes de comenzar la marcha, con el vehículo detenido")
                .font(.body)
                .foregroundColor(.primary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 10)
            
            Button(action: onDismiss) {
                Text("Cerrar")
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.blue)
                    .cornerRadius(12)
            }
            .padding(.top, 5)
        }
        .padding(24)
        .background(Color(UIColor.systemBackground))
        .cornerRadius(20)
        .shadow(color: Color.black.opacity(0.25), radius: 15, x: 0, y: 10)
        .padding(.horizontal, 35)
    }
}
struct ContactButton: View {
    @State private var showContactInfo = false
    
    var body: some View {
        VStack {
            Spacer()
            HStack {
                Spacer()
                Button(action: {
                    showContactInfo = true
                }) {
                    Label("Contacto", systemImage: "envelope.fill")
                        .font(.caption.bold())
                        .foregroundColor(.blue)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color(UIColor.systemBackground).opacity(0.95))
                        .clipShape(Capsule())
                        .shadow(color: Color.black.opacity(0.2), radius: 4, x: 0, y: 2)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 16)
        }
        .frame(maxWidth: UIScreen.main.bounds.width)
        .ignoresSafeArea(.keyboard)
        .alert("Contacto", isPresented: $showContactInfo) {
            Button("Cerrar", role: .cancel) { }
        } message: {
            Text("Este proyecto está dirigido por Elisa Pérez Moreno del Departamento de Psicobiología y Metodología en Ciencias del Comportamiento de la Facultad de Psicología (UCM).\n\nSi tienes alguna duda sobre este estudio, puedes dirigirte a nosotros a través del correo electrónico elisaperez@psi.ucm.es")
        }
    }
}

