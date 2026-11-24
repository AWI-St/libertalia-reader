package org.libertalia.reader.lector.modotexto

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Un trozo del texto ya limpio. */
sealed interface Bloque {
    data class Titulo(val texto: String) : Bloque

    /** [continua]: sigue el párrafo de la página anterior (que se cortó por el salto de página). */
    data class Parrafo(val texto: String, val continua: Boolean = false) : Bloque

    /** Nota al pie (se muestra en un recuadro con otro fondo). */
    data class Nota(val texto: String) : Bloque
}

/** Una página del PDF convertida en bloques de texto. */
data class PaginaLimpia(val numero: Int, val bloques: List<Bloque>)

/**
 * Convierte las líneas sueltas del PDF en texto de libro, con pistas sencillas:
 * - quita los números de página y las cabeceras o pies que se repiten en muchas páginas;
 * - las líneas con letra bastante más grande son títulos;
 * - las líneas de abajo con letra más pequeña que empiezan por número o asterisco son notas;
 * - rehace los párrafos (por el espacio entre líneas, la sangría y las líneas cortas)
 *   y une las palabras partidas con guion a final de línea.
 * Son pistas: en algún PDF pueden fallar.
 */
object LimpiezaTexto {

    // Número de página suelto («12», «- 12 -», «xii», o «2 9 0» en algunos escaneos).
    private val NUMERO_PAGINA = Regex("^[\\s\\-–—.·|]*([0-9]( ?[0-9]){0,3}|[ivxlcdm]{1,7})[\\s\\-–—.·|]*$", RegexOption.IGNORE_CASE)
    // Número de nota al principio de la línea («12 », «(3)», «¹»); no un año como «1978,».
    // Detrás del número tiene que venir texto (no «16 / AUTOR», que es una cabecera).
    private val MARCA_NOTA = Regex("^\\s*(${SUP_INICIO}?\\(?\\d{1,3}(?!\\d)(?![.,]\\d)\\)?${SUP_FIN}?[.)]?|[*†‡§]+)\\s*[\\p{L}«“\"(¿¡\\[‘'0-9]")
    // Fin de frase, aunque detrás venga una llamada a nota volada («posible».³).
    private val FIN_DE_FRASE = Regex("[.!?:;»”\"')\\]…]\\s*(${SUP_INICIO}[0-9*]+${SUP_FIN}\\s*)?$")

    /** Palabras de verdad que acaban en «í» (detrás de ellas sí va un espacio). */
    private val PALABRAS_EN_I = setOf(
        "así", "aquí", "allí", "ahí", "sí", "mí", "fui", "oí", "leí", "creí", "reí", "huí", "vi",
        "frenesí", "maniquí", "rubí", "esquí", "jabalí", "bisturí", "colibrí", "alhelí", "baladí",
        "israelí", "marroquí", "iraní", "guaraní", "ají", "zahorí", "carmesí", "benjuí",
    )
    private val I_PARTIDA = Regex("(\\p{L}*í) (?=\\p{Ll})")

    /**
     * [detectarNotas] = false: no se buscan notas al pie; todo el texto queda como párrafos
     * (para los PDF en los que la detección se equivoca).
     */
    fun limpiar(paginasOriginales: List<PaginaPdf>, detectarNotas: Boolean = true): List<PaginaLimpia> {
        val paginas = repararLigaduras(repararIPartida(paginasOriginales)).map { pagina ->
            pagina.copy(lineas = pagina.lineas.map { it.copy(texto = quitarEspaciosSobrantes(it.texto)) })
        }
        val cuerpo = tamanoCuerpo(paginas)
        // ¿El libro marca sus llamadas con números volados dentro del texto? (Los escaneos
        // casi nunca: el reconocimiento de texto las lee como números normales.)
        val conLlamadasVoladas = paginas.sumOf { p ->
            p.lineas.count { l -> l.texto.indexOf(SUP_INICIO).let { it >= 0 && l.texto.substring(0, it).isNotBlank() } }
        } >= 5
        val repetidas = lineasRepetidas(paginas)
        val sinCabeceras = paginas.map { pagina -> pagina.lineas.filterNot { esCabeceraOPie(it, pagina, repetidas) } }

        if (!detectarNotas) {
            return marcarContinuaciones(paginas.mapIndexed { i, pagina ->
                PaginaLimpia(pagina.numero, bloquesDe(recolocarFinales(sinCabeceras[i]), cuerpo))
            })
        }

        fun pasada(admitirSinNumero: Boolean): List<PaginaLimpia> {
            var anteriorConNotas = false
            return paginas.mapIndexed { i, pagina ->
                val lineas = sinCabeceras[i]
                // En los escaneos cada página puede tener un tamaño de letra algo mayor que otras:
                // entonces se usa el suyo (medido arriba de la página, donde no hay notas).
                val propio = tamanoCuerpo(listOf(pagina.copy(lineas = lineas)))
                val cuerpoPagina = if (lineas.sumOf { it.texto.length } >= 400) maxOf(cuerpo, propio) else cuerpo
                val limpia = if (anteriorConNotas && todaDeNota(lineas, cuerpoPagina)) {
                    // Una nota muy larga que ocupa toda esta página.
                    PaginaLimpia(pagina.numero, listOf(Bloque.Nota(lineas.map { it.texto }.reduce(::unir))))
                } else {
                    val (texto, notas) = separarNotas(lineas, pagina, cuerpoPagina, admitirSinNumero, conLlamadasVoladas, anteriorConNotas)
                    PaginaLimpia(pagina.numero, notasFinales(bloquesDe(texto, cuerpoPagina)) + notas.map { Bloque.Nota(it) })
                }
                anteriorConNotas = limpia.bloques.lastOrNull() is Bloque.Nota
                limpia
            }
        }

        // Si el libro numera sus notas, un grupo de letra pequeña sin número no es una nota
        // (será, por ejemplo, un texto de un prólogo con otra letra).
        var limpias = pasada(admitirSinNumero = true)
        val notas = limpias.flatMap { p -> p.bloques.filterIsInstance<Bloque.Nota>() }
        val numeradas = notas.count { MARCA_NOTA.containsMatchIn(it.texto) }
        if (numeradas >= 3 && numeradas >= notas.size - numeradas) limpias = pasada(admitirSinNumero = false)
        return marcarContinuaciones(marcarLlamadasNormales(comprobarLlamadas(juntarNotasPartidas(limpias))))
            .map { p -> p.copy(bloques = p.bloques.map { if (it is Bloque.Titulo) Bloque.Titulo(numeroDeApartado(it.texto)) else it }) }
    }

    /**
     * El número de apartado al principio de un título («¹ — Precocidad», que un escaneo leyó
     * como volado) se deja normal, sin la comilla suelta que a veces queda delante («' ¹ —»).
     */
    private fun numeroDeApartado(texto: String): String =
        texto.replace(APARTADO_VOLADO) { "${it.groupValues[1]}${it.groupValues[2]}" }

    private val APARTADO_VOLADO = Regex("^\\s*(?:['’‘`´]\\s*)?$SUP_INICIO([0-9]{1,3})$SUP_FIN(\\S?)")

    /** Las llamadas voladas de verdad: en un párrafo o título, pero no al principio (no «¹ — Apartado»). */
    private fun llamadasVoladas(bloque: Bloque): List<Int> {
        val texto = (bloque as? Bloque.Parrafo)?.texto ?: (bloque as? Bloque.Titulo)?.texto ?: return emptyList()
        return LLAMADA_VOLADA.findAll(texto)
            .filter { m -> texto.substring(0, m.range.first).isNotBlank() }
            .map { it.groupValues[1].toInt() }.toList()
    }

    /**
     * Algunos libros (sobre todo escaneos) escriben la llamada con un número normal
     * («el Informe”. 1 Muy tarde», «autores)3»). Si en la página hay una nota con ese número y
     * en el texto aparece detrás de un signo, se marca como volada: así se ve pequeña y
     * la nota se coloca detrás del párrafo que la llama.
     */
    private fun marcarLlamadasNormales(paginas: List<PaginaLimpia>): List<PaginaLimpia> {
        // Solo si el libro no marca ya sus llamadas con números volados.
        if (usaLlamadasVoladas(paginas)) return paginas
        return paginas.map { p ->
            val numeros = p.bloques.filterIsInstance<Bloque.Nota>().mapNotNull { nota ->
                if (nota.texto.trimStart().startsWith(SUP_INICIO)) null
                else NUMERO_DE_NOTA.find(nota.texto)?.groupValues?.get(1)
            }
            if (numeros.isEmpty()) return@map p
            val bloques = p.bloques.toMutableList()
            numeros.forEach { numero ->
                // Pegada al signo («autores)3», «social.9») o con un espacio y detrás una
                // mayúscula o el final («el Informe”. 1 Muy»); no «art. 20.», que es un artículo.
                val signo = "(?<=[.,;:)\\]”»\"'’?!…])"
                val llamada = Regex("$signo$numero(?![0-9])(?=\\s|$|[.,;:)])|$signo\\s$numero(?=\\s+[\\p{Lu}¿¡«“\"(]|\\s*$)")
                val i = bloques.indexOfFirst { it is Bloque.Parrafo && llamada.containsMatchIn(it.texto) }
                if (i >= 0) {
                    val b = bloques[i] as Bloque.Parrafo
                    bloques[i] = b.copy(texto = llamada.replaceFirst(b.texto, "$SUP_INICIO$numero$SUP_FIN"))
                }
            }
            PaginaLimpia(p.numero, bloques)
        }
    }

    /**
     * Si el libro marca sus llamadas con números volados, una «nota» con número normal
     * que nadie llama cerca («5 de diciembre de 1936...», una tabla) no es una nota.
     */
    private fun comprobarLlamadas(paginas: List<PaginaLimpia>): List<PaginaLimpia> {
        if (!usaLlamadasVoladas(paginas)) return paginas
        val llamadas = llamadasPorPagina(paginas)
        return paginas.mapIndexed { i, p ->
            val cerca = llamadasCerca(llamadas, i)
            PaginaLimpia(p.numero, p.bloques.map { b ->
                if (b !is Bloque.Nota || b.texto.trimStart().startsWith(SUP_INICIO)) return@map b
                val numero = NUMERO_DE_NOTA.find(b.texto)?.groupValues?.get(1)?.toIntOrNull() ?: return@map b
                if (numero in cerca) b else Bloque.Parrafo(b.texto)
            })
        }
    }

    private fun llamadasPorPagina(paginas: List<PaginaLimpia>) =
        paginas.map { p -> p.bloques.flatMap(::llamadasVoladas).toSet() }

    /** Las llamadas de esta página y de las dos anteriores. */
    private fun llamadasCerca(llamadas: List<Set<Int>>, i: Int) =
        (maxOf(0, i - 2)..i).flatMap { llamadas[it] }.toSet()

    /**
     * Si el libro marca sus llamadas con números volados: hay unas cuantas y al menos la
     * mitad de las notas numeradas tienen la suya en el libro. (En un escaneo, los números de
     * apartado de los títulos pueden leerse como volados —«¹ — Precocidad de la
     * sexualidad»— sin que el libro use llamadas voladas.)
     */
    private fun usaLlamadasVoladas(paginas: List<PaginaLimpia>): Boolean {
        val llamadas = llamadasPorPagina(paginas)
        if (llamadas.sumOf { it.size } < 3) return false
        val todas = llamadas.flatten().toSet()
        val numeros = paginas.flatMap { p ->
            p.bloques.filterIsInstance<Bloque.Nota>().mapNotNull { NUMERO_DE_NOTA.find(it.texto)?.groupValues?.get(1)?.toIntOrNull() }
        }
        return numeros.count { it in todas } * 2 >= numeros.size
    }

    private val LLAMADA_VOLADA = Regex("${SUP_INICIO}([0-9]{1,3})${SUP_FIN}")

    /** Si toda la página tiene la letra pequeña de las notas y empieza a media frase. */
    private fun todaDeNota(lineas: List<LineaPdf>, cuerpo: Float): Boolean {
        if (lineas.isEmpty()) return false
        val tamano = lineas.first().tamano
        // Sin ninguna nota nueva dentro (si las hay, es una página de notas al final del capítulo).
        return tamano <= cuerpo * 0.88f && lineas.all { abs(it.tamano - tamano) <= 0.6f } &&
            lineas.first().texto.trimStart().firstOrNull()?.isLowerCase() == true &&
            lineas.none { it.texto.trimStart().startsWith(SUP_INICIO) || MARCA_NOTA.containsMatchIn(it.texto) }
    }

    /**
     * Un párrafo que empieza con un número volado («¹ Véase...») es una nota, aunque tenga
     * la letra del texto: así son las notas reunidas al final de un capítulo.
     */
    private fun notasFinales(bloques: List<Bloque>): List<Bloque> = bloques.map { b ->
        if (b is Bloque.Parrafo && EMPIEZA_VOLADA.containsMatchIn(b.texto)) Bloque.Nota(b.texto) else b
    }

    private val EMPIEZA_VOLADA = Regex("^\\s*${SUP_INICIO}[0-9]{1,3}${SUP_FIN}\\s*[\\p{Lu}«“\"(¿¡\\[]")

    /**
     * Algunos PDF dejan un espacio dentro de las palabras con ligaduras («Th e», «fi nanzas»,
     * «signifi cativo»). Si pasa muchas veces en el libro, se quita.
     */
    private fun repararLigaduras(paginas: List<PaginaPdf>): List<PaginaPdf> {
        val veces = paginas.sumOf { p -> p.lineas.sumOf { l -> LIGADURA_PARTIDA.findAll(l.texto).count() } }
        if (veces < 5) return paginas
        return paginas.map { pagina ->
            pagina.copy(lineas = pagina.lineas.map { it.copy(texto = LIGADURA_PARTIDA.replace(it.texto, "$1")) })
        }
    }

    // («ff» no: en inglés «off the» se juntaría mal.)
    private val LIGADURA_PARTIDA = Regex("(ffi|ffl|fi|fl|\\bTh) (?=\\p{Ll})")

    /**
     * Una nota larga puede seguir al pie de la página siguiente. Si las notas llevan número
     * volado y la primera de una página no lo lleva, es el final de la última nota anterior.
     */
    private fun juntarNotasPartidas(paginas: List<PaginaLimpia>): List<PaginaLimpia> {
        val resultado = paginas.map { it.bloques.toMutableList() }
        var anterior: Pair<Int, Int>? = null // página e índice de la última nota vista
        var paginaUltimaNota = -10 // última página con notas (aunque se hayan juntado con otra)
        resultado.forEachIndexed { p, bloques ->
            val notas = bloques.indices.filter { bloques[it] is Bloque.Nota }
            if (notas.isEmpty()) return@forEachIndexed
            val primera = (bloques[notas.first()] as Bloque.Nota).texto.trimStart()
            val previa = anterior
            // Solo con la página justo anterior, y si allí las notas llevaban número.
            fun conNumero(texto: String) = texto.startsWith(SUP_INICIO) || MARCA_NOTA.containsMatchIn(texto)
            if (previa != null && paginaUltimaNota == p - 1 && !conNumero(primera)) {
                val (pp, ii) = previa
                val vieja = resultado[pp][ii] as Bloque.Nota
                // (No si la anterior acabó la frase y esta empieza en mayúscula: será una nota
                // nueva a la que el escaneo le borró el número.)
                val nueva = FIN_DE_FRASE.containsMatchIn(vieja.texto) && primera.firstOrNull()?.isUpperCase() == true
                if (conNumero(vieja.texto.trimStart()) && !nueva) {
                    resultado[pp][ii] = Bloque.Nota(unir(vieja.texto, primera))
                    bloques.removeAt(notas.first())
                }
            }
            paginaUltimaNota = p
            val ultima = bloques.indexOfLast { it is Bloque.Nota }
            anterior = if (ultima >= 0) p to ultima else anterior
        }
        return paginas.mapIndexed { p, pagina -> PaginaLimpia(pagina.numero, resultado[p]) }
    }

    /**
     * Algunos PDF dejan un espacio de más detrás de la «í» («dí as», «sí ndrome»).
     * Si pasa muchas veces en el libro, se quita ese espacio, salvo detrás de palabras
     * que de verdad acaban en «í» («así que», «aquí está»).
     */
    private fun repararIPartida(paginas: List<PaginaPdf>): List<PaginaPdf> {
        val texto = paginas.asSequence().flatMap { it.lineas }.map { it.texto }
        val totalI = texto.sumOf { linea -> linea.count { it == 'í' } }
        val partidas = texto.sumOf { linea ->
            I_PARTIDA.findAll(linea).count { it.groupValues[1].lowercase() !in PALABRAS_EN_I }
        }
        if (partidas < 5 || partidas < totalI * 0.25) return paginas
        return paginas.map { pagina ->
            pagina.copy(lineas = pagina.lineas.map { linea ->
                linea.copy(texto = I_PARTIDA.replace(linea.texto) { m ->
                    if (m.groupValues[1].lowercase() in PALABRAS_EN_I) m.value else m.groupValues[1]
                })
            })
        }
    }

    private val ESPACIO_ANTES = Regex("\\s+([,.;:!?)\\]”»])")
    private val ESPACIO_DESPUES = Regex("([(\\[“«¡¿])\\s+")

    /** Quita los espacios que algunos PDF dejan antes de «, . ; )» o después de «( “ ¿». */
    private fun quitarEspaciosSobrantes(texto: String): String =
        texto.replace(ESPACIO_ANTES, "$1").replace(ESPACIO_DESPUES, "$1")

    /** El tamaño de letra del texto normal: el que más letras tiene. */
    private fun tamanoCuerpo(paginas: List<PaginaPdf>): Float {
        // Se mide en la parte de arriba de cada página (sin cabecera), donde casi nunca hay
        // notas: en libros con muchas notas, su letra podría ser la más abundante.
        val arriba = paginas.flatMap { p -> p.lineas.filter { it.y > p.alto * 0.15f && it.y < p.alto * 0.6f } }
        val lineas = if (arriba.sumOf { it.texto.length } >= 300) arriba else paginas.flatMap { it.lineas }
        return lineas
            .groupBy { (it.tamano * 2).roundToInt() / 2f }
            .maxByOrNull { (_, grupo) -> grupo.sumOf { it.texto.length } }
            ?.key ?: 10f
    }

    private fun enZonaDeArriba(linea: LineaPdf, pagina: PaginaPdf) = linea.y < pagina.alto * 0.15f
    private fun enZonaDeAbajo(linea: LineaPdf, pagina: PaginaPdf) = linea.y > pagina.alto * 0.9f

    /**
     * Lo que identifica una cabecera o pie aunque cambie el número de página o el
     * reconocimiento de texto de un escaneo lo lea un poco distinto («2 9 0 © Revista», «Revista,
     * nüm.»): solo las letras, sin tildes, sin números (tampoco romanos) ni signos.
     */
    private fun claveRepeticion(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\b[ivxlcdm]+\\b"), "")
            .filter { it in 'a'..'z' }

    /** Textos de arriba o abajo de la página que aparecen en muchas páginas (cabeceras y pies). */
    private fun lineasRepetidas(paginas: List<PaginaPdf>): Set<String> {
        if (paginas.size < 4) return emptySet()
        val cuentas = mutableMapOf<String, Int>()
        paginas.forEach { pagina ->
            pagina.lineas
                .filter { enZonaDeArriba(it, pagina) || enZonaDeAbajo(it, pagina) }
                .map { claveRepeticion(it.texto) }
                .filter { it.length >= 4 }
                .toSet()
                .forEach { cuentas[it] = (cuentas[it] ?: 0) + 1 }
        }
        val minimo = maxOf(3, ceil(paginas.size * 0.25).toInt())
        return cuentas.filterValues { it >= minimo }.keys
    }

    private fun esCabeceraOPie(linea: LineaPdf, pagina: PaginaPdf, repetidas: Set<String>): Boolean {
        if (linea.texto.isBlank()) return true
        // La rayita que separa el texto de las notas («_____»).
        if (linea.texto.all { it in "_-–—. " }) return true
        if (!enZonaDeArriba(linea, pagina) && !enZonaDeAbajo(linea, pagina)) return false
        if (NUMERO_PAGINA.matches(linea.texto)) return true
        val clave = claveRepeticion(linea.texto)
        // Casi igual que una repetida (en los escaneos alguna letra se lee mal: «VNED»).
        return clave in repetidas || repetidas.any { parecidas(it, clave) }
    }

    /** Si dos textos largos se diferencian en muy pocas letras (como mucho una de cada diez). */
    private fun parecidas(a: String, b: String): Boolean {
        if (a.length < 10 || b.length < 10 || abs(a.length - b.length) > a.length / 10) return false
        // Distancia de edición (letras que hay que cambiar, poner o quitar).
        var anterior = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val actual = IntArray(b.length + 1)
            actual[0] = i
            for (j in 1..b.length) {
                val cambio = if (a[i - 1] == b[j - 1]) 0 else 1
                actual[j] = minOf(anterior[j] + 1, actual[j - 1] + 1, anterior[j - 1] + cambio)
            }
            anterior = actual
        }
        return anterior[b.length] <= maxOf(1, a.length / 10)
    }

    /**
     * Separa las notas al pie: el grupo de líneas de la parte baja de la página con letra
     * más pequeña que el texto, desde la primera que empieza por número o asterisco.
     * Si ninguna empieza así (en los escaneos el número volado suele leerse mal, como «'»
     * o «^»), también vale un grupo de letra pequeña bien separado del texto por un hueco.
     */
    private fun separarNotas(
        lineas: List<LineaPdf>,
        pagina: PaginaPdf,
        cuerpo: Float,
        admitirSinNumero: Boolean,
        conLlamadasVoladas: Boolean,
        anteriorConNotas: Boolean,
    ): Pair<List<LineaPdf>, List<String>> {
        if (lineas.size < 2) return separarNotasSinResto(lineas, pagina, cuerpo, admitirSinNumero, conLlamadasVoladas, anteriorConNotas)
        // Un escaneo puede dejar debajo de las notas un resto diminuto («l ^ r O T l M O»):
        // se aparta para medir bien las notas y luego se pone al final de la última.
        val todas = recolocarFinales(lineas)
        val ultima = todas.last()
        val penultima = todas[todas.size - 2]
        val esResto = ultima.texto.trim().length < 20 && ultima.tamano < penultima.tamano - 0.6f &&
            penultima.tamano <= cuerpo * 0.92f
        if (!esResto) return separarNotasSinResto(lineas, pagina, cuerpo, admitirSinNumero, conLlamadasVoladas, anteriorConNotas)
        val (texto, notas) = separarNotasSinResto(todas.dropLast(1), pagina, cuerpo, admitirSinNumero, conLlamadasVoladas, anteriorConNotas)
        return if (notas.isEmpty()) (texto + ultima) to notas
        else texto to (notas.dropLast(1) + unir(notas.last(), ultima.texto))
    }

    private fun separarNotasSinResto(
        lineas: List<LineaPdf>,
        pagina: PaginaPdf,
        cuerpo: Float,
        admitirSinNumero: Boolean,
        conLlamadasVoladas: Boolean,
        anteriorConNotas: Boolean,
    ): Pair<List<LineaPdf>, List<String>> {
        if (lineas.isEmpty()) return lineas to emptyList()
        val lineas = recolocarFinales(lineas)
        // Las notas tienen todas el mismo tamaño de letra: así no se cuela una cita del texto
        // que esté encima con letra solo un poco menor.
        val tamanoNotas = lineas.last().tamano
        var inicioPequenas = lineas.size
        while (inicioPequenas > 0) {
            val l = lineas[inicioPequenas - 1]
            val pequena = l.tamano <= cuerpo * 0.92f && abs(l.tamano - tamanoNotas) <= 0.6f
            if (pequena && l.y > pagina.alto * 0.3f) inicioPequenas-- else break
        }
        if (inicioPequenas == lineas.size) return lineas to emptyList()
        val primeraMarca = (inicioPequenas until lineas.size).firstOrNull {
            MARCA_NOTA.containsMatchIn(lineas[it].texto) && !NO_ES_NUMERO.containsMatchIn(lineas[it].texto)
        }
        // Si el grupo pequeño está bien separado del texto, empieza ahí aunque su primera
        // línea no lleve número (escaneos); si no, en la primera línea con número.
        // En un libro con notas numeradas, un grupo que no empieza con número solo vale si
        // sigue una nota de la página anterior (empieza en minúscula, o esa página acabó en
        // nota), si más abajo hay notas numeradas o si empieza con la sangría de una nota
        // (en los escaneos el número puede perderse).
        val primeraLinea = lineas[inicioPequenas].texto.trimStart()
        val resto = lineas.subList(inicioPequenas + 1, lineas.size)
        val conSangria = resto.isNotEmpty() && lineas[inicioPequenas].let { l ->
            val margenResto = resto.minOf { it.x }
            l.x > margenResto + l.tamano * 0.6f && l.x < margenResto + l.tamano * 4f
        }
        val grupoValido = admitirSinNumero || MARCA_NOTA.containsMatchIn(primeraLinea) ||
            MARCA_ESCANEO.containsMatchIn(primeraLinea) ||
            primeraLinea.firstOrNull()?.isLowerCase() == true ||
            anteriorConNotas || !conLlamadasVoladas && (primeraMarca != null || conSangria)
        val inicio = inicioPequenas.takeIf { grupoValido && separadoDelTexto(lineas, it, cuerpo) } ?: primeraMarca
            ?: return lineas to emptyList()
        // Un índice («7) Lagunas de la teoría........ 52») no son notas, aunque sus líneas
        // empiecen por número y tengan letra pequeña.
        val enIndice = (inicio until lineas.size).count { LINEA_DE_INDICE.containsMatchIn(lineas[it].texto) }
        if (enIndice * 2 >= lineas.size - inicio) return lineas to emptyList()

        // Una nota nueva empieza con su número (o lo que el escaneo dejó de él)
        // o con la primera línea metida hacia dentro.
        val grupo = lineas.subList(inicio, lineas.size)
        val margen = grupo.minOf { it.x }
        // Si las notas llevan el número volado, solo así empieza una nota: una línea que
        // sigue la nota y empieza por una cifra («20. 1 a) CE…») no es una nota nueva.
        val conVoladas = grupo.any { it.texto.trimStart().startsWith(SUP_INICIO) }
        // ¿Las notas van numeradas? (Un asterisco suelto no cuenta.)
        val hayNumeros = conVoladas || grupo.any { MARCA_NOTA.containsMatchIn(it.texto) && NUMERO_DE_NOTA.containsMatchIn(it.texto) }
        // Si las notas empiezan con la primera línea metida hacia dentro («    4 Ver...»).
        fun sangrado(l: LineaPdf) = l.x > margen + l.tamano * 0.6f && l.x < margen + l.tamano * 4f
        val finGrupo = grupo.maxOf { it.xFin }
        val notas = mutableListOf<String>()
        var ultimoNumero: Int? = null
        var previa: LineaPdf? = null
        for (l in grupo) {
            val numero = NUMERO_DE_NOTA.find(l.texto)?.groupValues?.get(1)?.toIntOrNull()
            val conNumero = when {
                l.texto.trimStart().startsWith(SUP_INICIO) -> true
                !MARCA_NOTA.containsMatchIn(l.texto) -> false
                // No es número de nota: «6tro» (un escaneo que leyó mal «Otro») ni el final
                // de una cita de páginas que sigue la nota («237). Afirmar...»).
                NO_ES_NUMERO.containsMatchIn(l.texto) -> false
                // Entre notas voladas, un número normal solo vale si es el siguiente.
                conVoladas -> numero != null && ultimoNumero != null && numero == ultimoNumero + 1
                // Con números normales, han de seguir el orden (tras la 4 va la 5), salvo que
                // la línea lleve la sangría de una nota nueva (en los escaneos el número se
                // lee mal a menudo: «8» por «3»).
                numero != null && ultimoNumero != null ->
                    numero == ultimoNumero + 1 || sangrado(l)
                else -> true
            }
            // Sin números (escaneos), una nota empieza por los restos del número o por la sangría.
            val empieza = conNumero || admitirSinNumero && !hayNumeros &&
                (MARCA_ESCANEO.containsMatchIn(l.texto) || l.x > margen + l.tamano * 0.6f) ||
                // En un escaneo, una nota a la que se le borró el número: con sangría, en
                // mayúscula y detrás de una línea corta que acabó la frase.
                !conLlamadasVoladas && hayNumeros && !conVoladas && sangrado(l) && previa != null &&
                l.texto.trimStart().firstOrNull()?.isUpperCase() == true &&
                FIN_DE_FRASE.containsMatchIn(previa.texto) && previa.xFin < finGrupo - l.tamano * 3
            if (notas.isEmpty() || empieza) {
                notas += l.texto
                if (numero != null && (conNumero || notas.size == 1)) ultimoNumero = numero
            } else {
                notas[notas.lastIndex] = unir(notas.last(), l.texto)
            }
            previa = l
        }
        val limpias = notas.map {
            // Un espacio entre el número volado y el texto de la nota («¹STC» → «¹ STC»),
            // y fuera los restos de un número mal leído («'' Rallo» → «Rallo»).
            it.replace(NUMERO_PEGADO, "$1 ").replace(MARCA_ESCANEO, "")
        }
        return lineas.subList(0, inicio) to limpias
    }

    /**
     * Algunos PDF guardan al final de la página textos que están más arriba (el pie de una
     * foto, «Foto 1»). Se ponen en su sitio para que no tapen las notas, que van debajo.
     */
    private fun recolocarFinales(lineas: List<LineaPdf>): List<LineaPdf> {
        val lista = lineas.toMutableList()
        var veces = 0
        while (lista.size > 1 && veces < 5) {
            val ultima = lista.last()
            val masAbajo = lista.dropLast(1).maxOf { it.y }
            if (ultima.y >= masAbajo - ultima.tamano) break
            lista.removeAt(lista.lastIndex)
            val sitio = lista.indexOfFirst { it.y > ultima.y }.let { if (it < 0) lista.size else it }
            lista.add(sitio, ultima)
            veces++
        }
        return lista
    }

    /** Si entre el texto y la línea [indice] hay bastante más hueco que entre dos líneas normales. */
    private fun separadoDelTexto(lineas: List<LineaPdf>, indice: Int, cuerpo: Float): Boolean {
        if (indice == 0) return false
        val saltos = lineas.subList(0, indice).zipWithNext { a, b -> b.y - a.y }
            .filter { it > 0 && it < cuerpo * 3 }.sorted()
        val interlinea = if (saltos.isEmpty()) cuerpo * 1.3f else saltos[saltos.size / 2]
        // Con letra mucho más pequeña que el texto basta un hueco algo menor.
        val factor = if (lineas[indice].tamano <= cuerpo * 0.8f) 1.5f else 1.8f
        return lineas[indice].y - lineas[indice - 1].y > interlinea * factor
    }

    private val NO_ES_NUMERO = Regex("^\\s*\\d{1,3}(\\p{Ll}|\\)[.,;])")

    /** Línea de un índice: puntos (o rayas) seguidos del número de página. */
    private val LINEA_DE_INDICE = Regex("[.…·_-]{4,}\\s*[0-9ivxlc]{1,4}\\b", RegexOption.IGNORE_CASE)

    /** El número con el que empieza una nota (volado o no). */
    private val NUMERO_DE_NOTA = Regex("^\\s*${SUP_INICIO}?\\(?(\\d{1,3})(?!\\d)")

    private val NUMERO_PEGADO = Regex("^(\\s*${SUP_INICIO}[0-9*]+${SUP_FIN})(?=\\S)")

    /** Lo que un escaneo deja de un número volado al principio de una nota: «'», «''», «^», «°». */
    private val MARCA_ESCANEO = Regex("^\\s*['’‘`´^°ºª®\"”“]{1,3}\\s+")

    /** Títulos y párrafos de una página. */
    private fun bloquesDe(lineas: List<LineaPdf>, cuerpo: Float): List<Bloque> {
        if (lineas.isEmpty()) return emptyList()
        val normales = lineas.filter { it.tamano < cuerpo * 1.2f }
        // Margen izquierdo: donde empiezan casi todas las líneas.
        val margen = normales.groupBy { it.x.roundToInt() }.maxByOrNull { it.value.size }?.key?.toFloat()
            ?: lineas.minOf { it.x }
        // Final de línea habitual (las líneas más cortas suelen acabar párrafo).
        val finHabitual = normales.map { it.xFin }.sorted().let { if (it.isEmpty()) 0f else it[(it.size * 0.9).toInt().coerceAtMost(it.lastIndex)] }
        // Espacio habitual entre líneas.
        // (Solo entre líneas del tamaño del texto: si quedan líneas de letra pequeña, más
        // juntas, cada línea normal parecería un párrafo aparte.)
        val saltos = lineas.zipWithNext()
            .filter { (a, b) -> a.tamano >= cuerpo * 0.92f && b.tamano >= cuerpo * 0.92f }
            .map { (a, b) -> b.y - a.y }.filter { it > 0 && it < cuerpo * 3 }.sorted()
            .takeIf { it.size >= 5 }
            ?: lineas.zipWithNext { a, b -> b.y - a.y }.filter { it > 0 && it < cuerpo * 3 }.sorted()
        val interlinea = if (saltos.isEmpty()) cuerpo * 1.3f else saltos[saltos.size / 2]

        val bloques = mutableListOf<Bloque>()
        var anterior: LineaPdf? = null
        for (linea in lineas) {
            // (Una línea que empieza en minúscula sigue una frase: no es un título aunque un
            // escaneo la haya medido algo más grande.)
            val ultimo = bloques.lastOrNull()
            val salto = anterior?.let { linea.y - it.y } ?: 0f
            val grande = linea.tamano >= cuerpo * 1.2f
            val sigueTitulo = ultimo is Bloque.Titulo && grande && salto in 0f..(linea.tamano * 1.8f)
            val esTitulo = ((grande && linea.texto.length <= 150) || enMayusculas(linea.texto)) &&
                (linea.texto.firstOrNull { it.isLetter() }?.isLowerCase() != true || sigueTitulo ||
                    ROMANO_MINUSCULA.containsMatchIn(linea.texto)) &&
                // Ni una línea que empieza con paréntesis y sigue una frase sin acabar, sin hueco
                // encima («(andrógenos). ¿Provendría...» medida algo más grande en un escaneo).
                !(ultimo is Bloque.Parrafo && anterior != null && linea.texto.trimStart().startsWith("(") &&
                    anterior.texto.trimEnd().lastOrNull()?.let { it.isLetter() || it in "-­" } == true &&
                    salto in 0f..(interlinea * 1.3f))
            when {
                // Un título grande de dos líneas se junta (los de mayúsculas no: suelen ser
                // las líneas de un índice, cada una con su apartado).
                esTitulo && ultimo is Bloque.Titulo && linea.tamano >= cuerpo * 1.2f &&
                    salto in 0f..(linea.tamano * 1.8f) ->
                    bloques[bloques.lastIndex] = Bloque.Titulo(unir(ultimo.texto, linea.texto))
                esTitulo -> bloques += Bloque.Titulo(linea.texto)
                else -> {
                    val prev = anterior
                    val nuevoParrafo = ultimo !is Bloque.Parrafo || prev == null ||
                        salto > interlinea * 1.45f || salto < 0 ||
                        // Sangría al principio de la línea (más a la derecha que la anterior;
                        // así las citas largas, con todas sus líneas metidas, no se rompen).
                        (linea.x > margen + cuerpo * 0.8f && linea.x < margen + cuerpo * 8f &&
                            linea.x > prev.x + cuerpo * 0.8f) ||
                        // La línea anterior acabó la frase y era corta.
                        (FIN_DE_FRASE.containsMatchIn(prev.texto) && prev.xFin < finHabitual - cuerpo * 2.5f)
                    if (nuevoParrafo) bloques += Bloque.Parrafo(linea.texto)
                    else bloques[bloques.lastIndex] = Bloque.Parrafo(unir((ultimo as Bloque.Parrafo).texto, linea.texto))
                }
            }
            anterior = linea
        }
        return bloques
    }

    /** Un apartado numerado con romanos en minúscula («v. Impulsar...»): sí puede ser un título. */
    private val ROMANO_MINUSCULA = Regex("^\\s*[ivxlc]{1,6}[.)](\\s|$)")

    /** Una línea corta escrita toda en mayúsculas («II. DERECHO AL HONOR») suele ser un título. */
    private fun enMayusculas(texto: String): Boolean {
        val letras = texto.filter { it.isLetter() }
        return texto.length <= 100 && letras.length >= 6 && letras.all { it.isUpperCase() } &&
            texto.trim().contains(' ')
    }

    /** Un párrafo que no acabó en una página sigue en la siguiente. */
    private fun marcarContinuaciones(paginas: List<PaginaLimpia>): List<PaginaLimpia> {
        var ultimoParrafo: Bloque.Parrafo? = null
        return paginas.map { pagina ->
            val bloques = pagina.bloques.toMutableList()
            val primero = bloques.indexOfFirst { it !is Bloque.Nota }
            val previo = ultimoParrafo
            if (primero >= 0 && previo != null) {
                val bloque = bloques[primero]
                if (bloque is Bloque.Parrafo && !FIN_DE_FRASE.containsMatchIn(previo.texto)) {
                    bloques[primero] = bloque.copy(continua = true)
                }
            }
            bloques.lastOrNull { it !is Bloque.Nota }?.let { ultimoParrafo = it as? Bloque.Parrafo }
            PaginaLimpia(pagina.numero, bloques)
        }
    }

    /** Une dos trozos de texto; si el primero acaba en una palabra partida con guion, la junta. */
    fun unir(a: String, b: String): String = if (acabaPartida(a, b)) a.dropLast(1) + b else "$a $b"

    /**
     * Si [a] acaba en una palabra partida que sigue en [b]: con guion normal («pregun-»
     * + «tarnos») o con el guion invisible de partir palabras (U+00AD), que algunos PDF usan.
     */
    fun acabaPartida(a: String, b: String): Boolean {
        if (a.length < 2 || !a[a.length - 2].isLetter()) return false
        return when (a.last()) {
            '­' -> b.firstOrNull()?.isLetter() == true
            '-', '‐' -> b.firstOrNull()?.isLowerCase() == true
            else -> false
        }
    }
}
