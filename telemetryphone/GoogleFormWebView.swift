//
//  GoogleFormWebView.swift
//  telemetryphone
//
//  Created by Marino Vargas Zapata on 20/05/2026.
//


import SwiftUI
import WebKit

struct GoogleFormWebView: UIViewRepresentable {
    let pin: String
    let onFormCompleted: () -> Void
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.navigationDelegate = context.coordinator
        let url = URL(string: "https://docs.google.com/forms/d/e/1FAIpQLSfprEkELFvWVuvIZWOixYnMi2nAHYdQerqEmoxuiMTHvdH4vw/viewform")!
                webView.load(URLRequest(url: url))
                return webView
    }
    
    func updateUIView(_ uiView: WKWebView, context: Context) {}
    
    class Coordinator: NSObject, WKNavigationDelegate {
        var parent: GoogleFormWebView
        
        init(_ parent: GoogleFormWebView) {
            self.parent = parent
        }
        
        // Equivale a tu onPageFinished
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
                    if webView.url?.absoluteString.contains("formResponse") == true {
                        let js = "document.body.innerText.includes('¡Gracias por tu colaboración!');"
                        webView.evaluateJavaScript(js) { (result, error) in
                            if let isCompleted = result as? Bool, isCompleted {
                                self.parent.onFormCompleted()
                            }
                        }
                    }
                }
    }
}
