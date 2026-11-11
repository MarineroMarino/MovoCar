import SwiftUI

struct DataCollectionView: View {
    let pin: String
    
    // Instanciamos el ViewModel que maneja toda la lógica de sensores y subida
    @StateObject private var viewModel = TelemetryViewModel()
    
    // Control de la interfaz
    @State private var isBreathing = false
    @State private var showAudioPopup = false
    @State private var showStartPopup = true // Controla el popup inicial con imagen y texto
    @Environment(\.dismiss) var dismiss // Para volver a la pantalla anterior al terminar
    
    var body: some View {
        ZStack {
            // --- CAPA 1: FONDO ---
            Image("Background_Gemini")
                .resizable()
                .scaledToFill()
                .ignoresSafeArea()
            
            // --- CAPA 2: CONTENIDO PRINCIPAL (BOTÓN STOP) ---
            VStack(spacing: 40) {
                Spacer()
                
                // 1. Feedback visual en tiempo real
                
                
                // 2. Botón STOP animado
                Button(action: {
                    isBreathing = false
                    
                    if viewModel.isCollecting {
                        viewModel.toggleCollection()
                    }
                    
                    showAudioPopup = true
                }) {
                    Image(systemName: "stop.circle.fill")
                        .resizable()
                        .frame(width: 120, height: 120)
                        .foregroundColor(.red)
                        .scaleEffect(isBreathing ? 1.08 : 1.0)
                        .animation(
                            .easeInOut(duration: 1.0).repeatForever(autoreverses: true),
                            value: isBreathing
                        )
                }
                
                Spacer()
            }
            
            ContactButton()
            
            // --- CAPA 3: POPUP INICIAL CON IMAGEN Y TEXTO ---
            if showStartPopup {
                Color.black.opacity(0.45)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(.spring()) {
                            showStartPopup = false
                        }
                    }
                
                StartPopupView {
                    withAnimation(.spring()) {
                        showStartPopup = false
                    }
                }
                .transition(.scale.combined(with: .opacity))
                .zIndex(1)
            }
        }
        .onAppear {
            // Al entrar a la pantalla, configuramos el PIN e iniciamos automáticamente
            viewModel.pin = pin
            isBreathing = true
            
            if !viewModel.isCollecting {
                viewModel.toggleCollection()
            }
        }
        // 3. Popup de Grabación de Audio
        .sheet(isPresented: $showAudioPopup) {
            AudioPopupView(pin: pin, completionMessage: viewModel.completionMessage) {
                showAudioPopup = false
                dismiss()
            }
            .presentationDetents([.fraction(0.6), .medium])
            .interactiveDismissDisabled(true)
        }
        .navigationBarBackButtonHidden(true)
    }
}
