package org.libertalia.reader.comic

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** Sentido de lectura de un cómic, tal como se guarda en la base de datos. */
object SentidoLectura {
    /** De izquierda a derecha (cómic occidental). */
    const val IZQUIERDA_A_DERECHA = "ltr"

    /** De derecha a izquierda (manga). */
    const val DERECHA_A_IZQUIERDA = "rtl"
}

/**
 * Los datos que trae un cómic en su ComicInfo.xml (el formato que usan ComicRack,
 * Kavita, Komga...). Todo es opcional.
 */
data class FichaComic(
    val titulo: String? = null,
    val serie: String? = null,
    val numero: Int? = null,
    val autor: String? = null,
    val idioma: String? = null,
    /** true = de derecha a izquierda (manga); null = no lo dice. */
    val derechaAIzquierda: Boolean? = null,
) {
    companion object {
        fun leer(xml: String?): FichaComic {
            if (xml.isNullOrBlank()) return FichaComic()
            return runCatching {
                val datos = mutableMapOf<String, String>()
                val lector = Xml.newPullParser()
                lector.setInput(StringReader(xml.trimStart('﻿')))
                var etiqueta: String? = null
                while (lector.next() != XmlPullParser.END_DOCUMENT) {
                    when (lector.eventType) {
                        XmlPullParser.START_TAG -> etiqueta = lector.name
                        XmlPullParser.TEXT -> etiqueta?.let { datos[it] = lector.text.trim() }
                        XmlPullParser.END_TAG -> etiqueta = null
                    }
                }
                fun dato(nombre: String) = datos[nombre]?.takeIf { it.isNotBlank() }
                FichaComic(
                    titulo = dato("Title"),
                    serie = dato("Series"),
                    // «Number» puede ser «1», «01» o «1.5»: se queda la parte entera.
                    numero = (dato("Number") ?: dato("Volume"))?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() },
                    autor = dato("Writer") ?: dato("Penciller"),
                    idioma = dato("LanguageISO"),
                    derechaAIzquierda = when (dato("Manga")) {
                        "YesAndRightToLeft" -> true
                        "No" -> false
                        else -> null
                    },
                )
            }.getOrDefault(FichaComic())
        }

        private val TOMO_EN_NOMBRE = Regex(
            "(?i)(?:\\b(?:tomo|vol(?:umen|ume)?\\.?|n[º°o]\\.?|#)\\s*(\\d{1,4})\\b)|(?:[\\s_-](\\d{1,3})\\s*$)",
        )

        /**
         * Saca el número de tomo del nombre del archivo, si lo tiene:
         * «La ciudad sumergida 02» → 2, «Mareas Tomo 3» → 3, «Vol. 5» → 5.
         * Devuelve el título sin ese número y el número.
         */
        fun tomoDelNombre(titulo: String): Pair<String, Int?> {
            val encontrado = TOMO_EN_NOMBRE.find(titulo) ?: return titulo to null
            val numero = (encontrado.groupValues[1].ifEmpty { encontrado.groupValues[2] }).toIntOrNull()
                ?: return titulo to null
            val sinNumero = titulo.removeRange(encontrado.range).trim(' ', '-', '_', ',', '.', '·')
            return (sinNumero.ifEmpty { titulo }) to numero
        }
    }
}
