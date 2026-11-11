//
//  StartPopupView.swift
//  telemetryphone
//
//  Created by Marino Vargas Zapata on 15/10/2026.
//
import SwiftUI

struct StartPopupView: View {
    let onDismiss: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            
            // --- IMAGEN ---
            // Opción A: Usando un icono del sistema de Apple (SF Symbols)
            Image(systemName: "exclamationmark.triangle.fill")
                .resizable()
                .scaledToFit()
                .frame(height: 90)
                .foregroundColor(.orange)
                .padding(.top, 10)
            
            // Opción B: Si tienes tu propia imagen en la carpeta Assets.xcassets,
            // comenta las 5 líneas de arriba y descomenta estas 4 poniendo el nombre de tu archivo:
            /*
            Image("nombre_de_tu_imagen")
                .resizable()
                .scaledToFit()
                .frame(height: 120)
                .cornerRadius(10)
            */
            
            // --- TÍTULO ---
            Text("¡Grabación en curso!")
                .font(.title2)
                .bold()
                .multilineTextAlignment(.center)
            
            // --- TEXTO DESCRIPTIVO ---
            Text("Conduce con seguridad. No uses la app mientras conduces.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 10)
            
            // --- BOTÓN DE CIERRE ---
            Button(action: onDismiss) {
                Text("Entendido")
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
        .background(Color(UIColor.systemBackground)) // Se adapta a modo claro y oscuro de iOS
        .cornerRadius(20)
        .shadow(color: Color.black.opacity(0.25), radius: 15, x: 0, y: 10)
        .padding(.horizontal, 35) // Margen lateral respecto a los bordes del iPhone
    }
}
