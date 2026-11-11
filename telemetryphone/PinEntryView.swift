import SwiftUI

struct PinEntryView: View {
    @State private var pin: [String] = ["", "", "", ""]
    @FocusState private var focusedField: Int?
    @State private var navigateToTelemetry = false
    @State private var finalPin = ""

    var body: some View {
        NavigationStack {
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