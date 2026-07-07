//
//  PreparationView.swift
//  telemetryphone
//
//  Created by Marino Vargas Zapata on 20/05/2026.
//


import SwiftUI

struct PreparationView: View {
    let pin = "0000" // Recibimos el PIN de la vista anterior
    @AppStorage("encuesta_completada_num") private var encuestasCompletadas: String = ""
    @State private var showInstructions = false
    @State private var showSurvey = false
    @State private var isPlayEnabled = false
    @State private var navigateToCollection = false
    
    @Environment(\.dismiss) var dismiss
    
    var isSurveyCompleted: Bool {
        encuestasCompletadas.contains(pin)
    }
    
    var body: some View {
        NavigationStack {
            ZStack{
                Image("Background_Gemini")
                    .resizable() // Permite que la imagen cambie de tamaño
                    .scaledToFill() // Hace que llene todo el espacio sin deformarse
                    .ignoresSafeArea()
                
                VStack(spacing: 40) {
                    
                    Button(action: { showInstructions = true }) {
                        Label("Instrucciones", systemImage: "doc.text.magnifyingglass")
                    }
                    .buttonStyle(LargeRectangularButtonStyle(color: .white))
                    
                    Button(action: { showSurvey = true }) {
                        Label("Encuesta", systemImage: "clipboard.fill")
                    }
                    .buttonStyle(LargeRectangularButtonStyle(color: .white))
                    .opacity(!isSurveyCompleted ? 1.0 : 0.5)
                    .disabled(isSurveyCompleted)
                    
                    
                    // Navegación hacia la recogida de datos (tu RecogidaDatosActivity)
                    NavigationLink(destination: DataCollectionView(pin: pin)) {
                        Image(systemName: "play.circle.fill")
                            .resizable()
                            .frame(width: 100, height: 100)
                            .foregroundColor(isSurveyCompleted ? .green : .gray)
                    }
                    .disabled(!isSurveyCompleted)
                    .opacity(isSurveyCompleted ? 1.0 : 0.5)
                }
                .padding(.horizontal, 30)
                // Tu AlertDialog
                .alert("\nBienvenido al experimento", isPresented: $showInstructions) {
                    
                    Button("Continuar", role: .cancel) { }
                } message: {
                    Text("\nEl estudio consiste en contestar a un cuestionario sobre estilos de conducción y una vez finalizado, en al menos, dos o tres ocasiones en las que conduzcas, accede a esta APP y le des al botón de START.\n\nCon ello, registraremos datos técnicos del vehículo mientras conduces.\nRecuerda que los datos son absolutamente anónimos y serán tratados exclusivamente de manera global junto con las respuestas de otros conductores con fines de investigación.\n\nTrata de contestar a la encuesta y de conducir de manera lo más natural posible.\n\nAl pulsar el botón de continuar confirmas que has leído estas instrucciones y que participas en esta investigación de forma voluntaria. Cuando finalices los trayectos, tan solo recuerda parar la grabación pulsando el botón de STOP.\n\nTendrás la opción posteriormente (pulsando sobre un botón con micrófono) para dejarnos cualquier comentario de algún aspecto que desees añadir acerca del recorrido.\n\nGracias por participar en esta investigación.")
                }
                // Tu WebViewFormActivity
                .sheet(isPresented: $showSurvey) {
                    GoogleFormWebView(pin:pin, onFormCompleted: {
                        encuestasCompletadas += "[\(pin)]"
                        showSurvey = false
                        
                    })
                }
                .navigationDestination(isPresented: $navigateToCollection) {
                    DataCollectionView(pin: pin)
                }
                .navigationBarBackButtonHidden(true)
                
            }
        }
        }
    
}

struct LargeRectangularButtonStyle: ButtonStyle {
    var color: Color
    
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.title.bold())
            .foregroundColor(.blue)
            .padding(.vertical, 25) // Hace el botón más alto
            .frame(maxWidth: .infinity) // Hace el botón más ancho
            .background(color)
            .clipShape(RoundedRectangle(cornerRadius: 15)) // Rectangular con bordes suaves
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .opacity(configuration.isPressed ? 0.9 : 1.0)
    }
}
