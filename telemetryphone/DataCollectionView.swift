import SwiftUI
import MapKit

struct DataCollectionView: View {
    let pin: String
    
    // Instanciamos el ViewModel que maneja toda la lógica de sensores y subida
    @StateObject private var viewModel = TelemetryViewModel()
    
    // Control de la interfaz
    @State private var isBreathing = false
    @State private var showAudioPopup = false
    @Environment(\.dismiss) var dismiss // Para volver a la pantalla anterior al terminar
    
    var body: some View {
        ZStack{
            Image("Background_Gemini")
                .resizable() // Permite que la imagen cambie de tamaño
                .scaledToFill() // Hace que llene todo el espacio sin deformarse
                .ignoresSafeArea()
            VStack(spacing: 40) {
                // --- ZONA SUPERIOR: EL MAPA ---
                            Map(position: $viewModel.cameraPosition) {
                                // Esto muestra el punto azul exacto del usuario con su cono de dirección
                                UserAnnotation()
                            }
                            .mapControls {
                                // Añade botones nativos de Apple para centrar la vista o usar la brújula
                                MapUserLocationButton()
                                MapCompass()
                            }
                            // Hacemos que el mapa ocupe aprox el 55% de la pantalla
                            .frame(maxHeight: UIScreen.main.bounds.height * 0.55)
                            .clipShape(RoundedRectangle(cornerRadius: 20))
                            .shadow(radius: 5)
                            .padding([.horizontal, .top])
                            
                            Spacer()
                
                
                // 1. Feedback visual en tiempo real usando el statusText del ViewModel
                
                
                // 2. Botón STOP animado
                Button(action: {
                    // Detenemos animación
                    isBreathing = false
                    
                    // Detenemos recogida de datos y desencadenamos la generación del CSV y subida
                    if viewModel.isCollecting {
                        viewModel.toggleCollection()
                    }
                    
                    // Mostramos popup de audio
                    showAudioPopup = true
                }) {
                    Image(systemName: "stop.circle.fill")
                        .resizable()
                        .frame(width: 120, height: 120)
                        .foregroundColor(.red)
                    // Animación de respiración (crece un 8%)
                        .scaleEffect(isBreathing ? 1.08 : 1.0)
                        .animation(
                            .easeInOut(duration: 1.0).repeatForever(autoreverses: true),
                            value: isBreathing
                        )
                }
                
                Spacer()
            }}
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
            AudioPopupView(pin: pin) {
                // Esta clausura (closure) se ejecuta cuando el usuario termina en el popup
                showAudioPopup = false
                dismiss() // Cierra esta vista y vuelve al menú principal
            }
            .presentationDetents([.fraction(0.6), .medium]) // Simula el BottomSheet de Android
            .interactiveDismissDisabled(true) // Obliga al usuario a usar los botones, no puede deslizar para cerrar
        }
        .navigationBarBackButtonHidden(true) // Evitamos que salgan a mitad de viaje dándole atrás
    }
}
