//
//  PreparationView.swift
//  telemetryphone
//
//  Created by Marino Vargas Zapata on 20/05/2026.
//


import SwiftUI

struct PreparationView: View {
    let pin: String // Recibimos el PIN de la vista anterior
    @AppStorage("encuesta_completada_num") private var encuestasCompletadas: String = ""
    @AppStorage("instrucciones_completadas_num") private var instruccionesCompletadas: String = ""
    @State private var showInstructions = false
    @State private var showSurvey = false
    @State private var isPlayEnabled = false
    @State private var navigateToCollection = false
    
    @Environment(\.dismiss) var dismiss
    
    var isSurveyCompleted: Bool {
        encuestasCompletadas.contains("[\(pin)]") || encuestasCompletadas.contains(pin)
    }
    
    var isInstructionsCompleted: Bool {
        instruccionesCompletadas.contains("[\(pin)]")
    }
    
    var canStartRecording: Bool {
        isSurveyCompleted && isInstructionsCompleted
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
                            .foregroundColor(canStartRecording ? .green : .gray)
                    }
                    .disabled(!canStartRecording)
                    .opacity(canStartRecording ? 1.0 : 0.5)
                }
                .padding(.horizontal, 30)
                // Modal de Instrucciones y Consentimiento
                .sheet(isPresented: $showInstructions) {
                    InstructionsView(
                        isAlreadyAccepted: isInstructionsCompleted,
                        onAccept: {
                            if !isInstructionsCompleted {
                                instruccionesCompletadas += "[\(pin)]"
                            }
                            showInstructions = false
                        }
                    )
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
                .navigationBarBackButtonHidden(false)
                
                ContactButton()
            }
        }
    }
}

struct InstructionsView: View {
    let isAlreadyAccepted: Bool
    let onAccept: () -> Void
    
    @State private var checks: [Bool]
    
    private let checkTexts = [
        "Soy mayor de edad y tengo permiso de conducir en vigor.",
        "He recibido suficiente información sobre el estudio.",
        "Comprendo que mi participación es voluntaria.",
        "Comprendo que puedo retirarme del estudio cuando desee."
    ]
    
    init(isAlreadyAccepted: Bool, onAccept: @escaping () -> Void) {
        self.isAlreadyAccepted = isAlreadyAccepted
        self.onAccept = onAccept
        self._checks = State(initialValue: Array(repeating: isAlreadyAccepted, count: 4))
    }
    
    private var allChecked: Bool {
        checks.allSatisfy { $0 }
    }
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Bienvenido al experimento")
                        .font(.title2.bold())
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.top, 8)
                    
                    Text("Esta investigación tiene como objetivo estudiar los distintos estilos de conducción para poder mejorar tecnologías emergentes en conducción automatizada. Para ello, tu tarea consistirá en contestar a un cuestionario sobre estilos de conducción (aproximadamente 15 minutos) y, una vez finalizado, en al menos dos o tres ocasiones en las que vayas a conducir, acceder a esta APP y pulsar el botón de START.\n\nInicia la grabación con el vehículo detenido y no uses el móvil mientras conduces, la aplicación funciona en segundo plano y no es necesario tocarla.\n\nCon ello, registraremos datos técnicos del vehículo mientras conduces (magnetómetro,giroscopio, acelerómetro y la posición en latitud, longitud y altitud).\n\nTrata de contestar a la encuesta y de conducir de la manera más natural posible. Cuando finalices los trayectos, tan solo recuerda parar la grabación pulsando el botón de STOP.\n\nJusto después, tendrás la opción, si quieres de dejarnos cualquier comentario sobre algún aspecto que desees añadir acerca del recorrido, pulsando sobre un botón con el micrófono.\n\nRecuerda que los datos son absolutamente confidenciales y serán tratados exclusivamente de manera global junto con las respuestas de otros conductores con fines de investigación. En ningún caso se publicarán tus resultados individuales ni ningún tipo de información que pudiera identificarte siguiendo la ley de protección de datos (RGPD – Reglamento UE 2016/679). No registraremos ni tu nombre, ni tu DNI, ni la matrícula de tu vehículo, ni tu correo electrónico; tan solo usamos los 4 últimos dígitos de tu móvil para enlazar tu respuesta en la encuesta con los datos de conducción.\n\nLa participación en este estudio es estrictamente voluntaria y puedes retirarte en cualquier momento. Al finalizar el estudio, el equipo se compromete a explicar los resultados a todos los participantes que estén interesados en conocerlos. Para ello, tan solo hará falta que nos lo indiques a través del botón de contacto de la aplicación.\n\nEsta investigación forma parte del proyecto SEGVAUTO 5Gen. Innovando para la movilidad del futuro (TEC-2024/ECO-277), dentro de Ayudas destinadas a la realización de programas de actividades de I+D entre Grupos de Investigación de la Comunidad de Madrid en Tecnologías.\n\nConfirma lo siguiente y pulsa el botón de continuar.")
                        .font(.body)
                        .foregroundColor(.primary)
                    
                    Divider()
                        .padding(.vertical, 4)
                    
                    VStack(alignment: .leading, spacing: 14) {
                        ForEach(0..<checkTexts.count, id: \.self) { index in
                            Button(action: {
                                checks[index].toggle()
                            }) {
                                HStack(alignment: .top, spacing: 12) {
                                    Image(systemName: checks[index] ? "checkmark.square.fill" : "square")
                                        .font(.title3)
                                        .foregroundColor(checks[index] ? .blue : .secondary)
                                    
                                    Text(checkTexts[index])
                                        .font(.subheadline)
                                        .foregroundColor(.primary)
                                        .multilineTextAlignment(.leading)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    Text("Gracias por participar en esta investigación.")
                    Button(action: onAccept) {
                        Text("Continuar")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(allChecked ? Color.blue : Color.gray)
                            .cornerRadius(12)
                    }
                    .disabled(!allChecked)
                    .padding(.top, 10)
                }
                .padding(24)
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
