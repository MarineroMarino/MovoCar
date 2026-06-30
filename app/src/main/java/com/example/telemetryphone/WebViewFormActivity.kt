package com.example.telemetryphone

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WebViewFormActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview_form)

        val myWebView = findViewById<WebView>(R.id.webview_form)
        myWebView.settings.javaScriptEnabled = true

        myWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                if (url != null && url.contains("formResponse")) {
                    val scriptJavaScript = "(function() { return document.body.innerText.includes('¡Gracias por tu colaboración!'); })();"
                    view?.evaluateJavascript(scriptJavaScript) { resultado ->
                        // El resultado viene como un String "true" o "false"
                        if (resultado == "true") {

                            // ==========================================
                            // ¡AHORA SÍ! EL FORMULARIO ESTÁ COMPLETADO
                            // ==========================================

                            // Aquí cierras el WebView o pasas a la siguiente pantalla

                            setResult(Activity.RESULT_OK)
                            finish()
                        }
                    }

                }
            }
        }

        myWebView.loadUrl("https://docs.google.com/forms/d/e/1FAIpQLSfprEkELFvWVuvIZWOixYnMi2nAHYdQerqEmoxuiMTHvdH4vw/viewform?usp=header")
    }
}