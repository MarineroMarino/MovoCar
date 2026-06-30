import SwiftUI

struct PinEntryView: View {
    @State private var pin: String = ""
    @FocusState private var isFocused: Bool
    @State private var navigateToHome = false
    @State private var finalPin = ""
    
    var body: some View {
        NavigationStack {
            ZStack{
                Image("Background_Gemini")
                    .resizable() // Permite que la imagen cambie de tamaño
                    .scaledToFill() // Hace que llene todo el espacio sin deformarse
                    .ignoresSafeArea()
                VStack(spacing: 30) {
                    
                    Text("Introduce los 4 últimos \ndigitos de su número de telefono")
                        .font(.title)
                        .foregroundColor(Color.gray)
                        .multilineTextAlignment(.center)
                    
                    ZStack {
                        // 1. Campo de texto oculto que captura la entrada real del teclado
                        TextField("", text: $pin)
                            .keyboardType(.numberPad)
                            .textContentType(.oneTimeCode) // Optimiza para códigos SMS si fuera necesario
                            .focused($isFocused)
                            .opacity(0) // Lo hacemos invisible
                            .frame(width: 1, height: 1) // Reducimos su tamaño al mínimo
                            .onChange(of: pin) { newValue in
                                // Filtrar para permitir únicamente números
                                let filtered = newValue.filter { $0.isNumber }
                                
                                // Limitar la longitud máxima a 4 dígitos
                                if filtered.count > 4 {
                                    pin = String(filtered.prefix(4))
                                } else {
                                    pin = filtered
                                }
                                
                                // Si se completan los 4 dígitos, quitamos el foco y avanzamos
                                if pin.count == 4 {
                                    isFocused = false
                                    verificarPin()
                                }
                            }
                        
                        // 2. Interfaz visual: 4 cajitas simuladas que leen el estado del String único
                        HStack(spacing: 15) {
                            ForEach(0..<4, id: \.self) { index in
                                Text(getDigit(at: index))
                                    .font(.title)
                                    .frame(width: 50, height: 60)
                                    .background(Color.gray.opacity(0.2))
                                    .cornerRadius(10)
                                // Resaltamos con un borde la caja en la que el usuario está escribiendo
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                            .stroke(isFocused && pin.count == index ? Color.blue : Color.clear, lineWidth: 2)
                                    )
                                    .onTapGesture {
                                        // Si el usuario toca cualquier caja, forzamos el foco en el TextField oculto
                                        isFocused = true
                                    }
                            }
                        }
                    }
                }
            }
            .onAppear {
                // Reiniciar estados al entrar o regresar a la vista
                pin = ""
                finalPin = ""
                navigateToHome = false
                isFocused = true // Abre el teclado automáticamente
            }
            .navigationDestination(isPresented: $navigateToHome) {
                PreparationView(pin: finalPin)
            }
        }
    }
    
    // Función auxiliar para extraer de forma segura el carácter en cada posición
    private func getDigit(at index: Int) -> String {
        guard index < pin.count else { return "" }
        let stringIndex = pin.index(pin.startIndex, offsetBy: index)
        return String(pin[stringIndex])
    }
    
    private func verificarPin() {
        if pin.count == 4 {
            finalPin = pin
            navigateToHome = true
        }
    }
}

