"""
Crea un PDF de prueba que imita un libro impreso, para probar el modo texto:
cabecera repetida arriba, número de página abajo, palabras partidas con guion,
llamadas a nota voladas, notas al pie con letra pequeña, títulos y párrafos que
siguen en la página siguiente. El texto es inventado (sin derechos de autor).

Uso: python herramientas/crear_pdf_notas.py
"""

import os

SALIDA = os.path.join(os.path.dirname(__file__), "libros-prueba")
ANCHO, ALTO = 300, 430  # libro de bolsillo
MARGEN = 32
CUERPO, INTERLINEA = 11, 14.5
NOTA, INTERLINEA_NOTA = 8.5, 11
CARACTERES = 47  # por línea de cuerpo
CARACTERES_NOTA = 58

# Texto: (tipo, contenido). En los párrafos, «{n}» es una llamada a la nota n.
TEXTO = [
    ("titulo", "Capítulo primero. Del arte de leer despacio"),
    ("parrafo", "Hay libros que se leen deprisa, como quien cruza una plaza con prisa por llegar a otra parte, y libros que piden detenerse en cada esquina.{1} Los segundos suelen ser los que más tarde recordamos, porque nos obligaron a mirar con atención las piedras del camino y a preguntarnos quién las había puesto allí y con qué propósito."),
    ("parrafo", "La lectura lenta no es una técnica sino una costumbre: se aprende como se aprende a caminar por el monte, equivocándose de sendero muchas veces y volviendo sobre los propios pasos sin impaciencia.{2} Quien lee así descubre que los libros contienen otros libros, citas, ecos y alusiones que en una primera lectura pasaban completamente desapercibidos."),
    ("parrafo", "Conviene, por tanto, acompañar la lectura de un cuaderno. En él se anotan las frases que sorprenden, las dudas, las palabras desconocidas y las ideas que el texto despierta aunque no estén escritas en él.{3} Con los años, ese cuaderno se convierte en una especie de biblioteca personal, más pequeña y más fiel que cualquier estantería, que conserva no lo que leímos sino lo que nos pasó al leerlo, y que podemos volver a recorrer cuando queramos recordar quiénes éramos en cada época de nuestra vida lectora."),
    ("titulo", "Capítulo segundo. Las notas al pie"),
    ("parrafo", "Las notas al pie tienen mala fama entre los lectores apresurados, que las consideran una interrupción innecesaria.{4} Sin embargo, en ellas se esconde a menudo lo mejor de un ensayo: la anécdota que el autor no se atrevió a poner en el texto principal, la referencia exacta, la discusión con otros estudiosos o la confesión de una duda."),
    ("parrafo", "Leer las notas exige un pequeño esfuerzo, el de bajar la vista y volver a subirla sin perder el hilo. En los libros electrónicos ese movimiento se complica, porque la página cambia de tamaño y las notas pueden quedar lejos del lugar al que se refieren.{5} Por eso conviene que se distingan bien del texto principal, con otro fondo o con letra más pequeña, para que el lector decida en cada momento si quiere leerlas o seguir adelante."),
    ("parrafo", "Un buen sistema de notas respeta las dos maneras de leer: la del que avanza sin detenerse y la del que quiere saberlo todo. Ninguna de las dos es mejor que la otra, y un mismo lector puede practicar ambas en el mismo libro, según el día, el cansancio o el interés que despierte cada capítulo."),
]

NOTAS = {
    1: "Véase, sobre esta distinción, cualquier tratado clásico de retórica; la idea es antigua y se repite en casi todas las épocas.",
    2: "La comparación con el monte es habitual en los manuales de lectura del siglo pasado.",
    3: "Algunos lectores prefieren escribir directamente en los márgenes del libro, costumbre que los bibliotecarios no suelen aprobar.",
    4: "Esta opinión aparece con frecuencia en las reseñas de ensayos académicos.",
    5: "En los formatos de texto adaptable, la página es una ventana que se mueve sobre un texto continuo.",
}


def partir(texto, ancho):
    """Reparte el texto en líneas; si una palabra larga no cabe, la parte con guion."""
    palabras = texto.split(" ")
    lineas, actual = [], ""
    for palabra in palabras:
        visible = palabra.replace("{", "").replace("}", "")
        largo_actual = len(actual.replace("{", "").replace("}", ""))
        if not actual:
            actual = palabra
        elif largo_actual + 1 + len(visible) <= ancho:
            actual += " " + palabra
        elif len(visible) >= 8 and "{" not in palabra and largo_actual + 5 <= ancho:
            hueco = ancho - largo_actual - 2
            corte = max(3, min(hueco, len(palabra) - 3))
            lineas.append(actual + " " + palabra[:corte] + "-")
            actual = palabra[corte:]
        else:
            lineas.append(actual)
            actual = palabra
    if actual:
        lineas.append(actual)
    return lineas


def texto_pdf(t):
    b = t.encode("cp1252")
    return b.replace(b"\\", b"\\\\").replace(b"(", b"\\(").replace(b")", b"\\)")


def linea_con_notas(texto, x, y, tamano, fuente=b"F1"):
    """Una línea de texto; las «{n}» se escriben voladas y más pequeñas."""
    partes = [b"BT /" + fuente + b" %g Tf %g %g Td" % (tamano, x, y)]
    resto = texto
    while "{" in resto:
        antes, _, despues = resto.partition("{")
        numero, _, resto = despues.partition("}")
        if antes:
            partes.append(b"(" + texto_pdf(antes) + b") Tj")
        partes.append(b"/F1 %g Tf %g Ts (" % (tamano * 0.65, tamano * 0.4) + texto_pdf(numero) + b") Tj 0 Ts /" + fuente + b" %g Tf" % tamano)
    if resto:
        partes.append(b"(" + texto_pdf(resto) + b") Tj")
    partes.append(b"ET")
    return b" ".join(partes)


def notas_de(linea):
    return [int(n) for n in __import__("re").findall(r"\{(\d+)\}", linea)]


def crear():
    # Líneas del cuerpo: (tipo, texto, sangría)
    lineas = []
    for tipo, contenido in TEXTO:
        if tipo == "titulo":
            lineas.append(("hueco", "", 0))
            lineas.append(("titulo", contenido, 0))
            lineas.append(("hueco", "", 0))
        else:
            for i, l in enumerate(partir(contenido, CARACTERES)):
                lineas.append(("cuerpo", l, 1 if i == 0 else 0))

    paginas = []  # cada una: lista de órdenes PDF
    abajo_cuerpo = 70
    i = 0
    numero = 0
    while i < len(lineas):
        numero += 1
        ordenes = [
            # Cabecera repetida y número de página.
            b"BT /F3 8 Tf %d %d Td (" % (MARGEN, ALTO - 30) + texto_pdf("Ensayo sobre la lectura") + b") Tj ET",
            b"BT /F1 9 Tf %d %d Td (%d) Tj ET" % (ANCHO // 2 - 4, 30, numero),
        ]
        y = ALTO - 60
        notas_pagina = []
        while i < len(lineas):
            tipo, texto, sangria = lineas[i]
            alto_linea = 22 if tipo == "titulo" else (8 if tipo == "hueco" else INTERLINEA)
            nuevas = notas_de(texto)
            alto_notas = sum(len(partir(f"{n} {NOTAS[n]}", CARACTERES_NOTA)) for n in notas_pagina + nuevas) * INTERLINEA_NOTA
            if y - alto_linea < abajo_cuerpo + alto_notas + (12 if notas_pagina or nuevas else 0):
                break
            if tipo == "titulo":
                y -= 6
                ordenes.append(b"BT /F2 15 Tf %d %d Td (" % (MARGEN, y) + texto_pdf(texto) + b") Tj ET")
                y -= 16
            elif tipo == "hueco":
                y -= 8
            else:
                ordenes.append(linea_con_notas(texto, MARGEN + (16 if sangria else 0), y, CUERPO))
                y -= INTERLINEA
            notas_pagina += nuevas
            i += 1
        # Notas al pie, abajo, con letra pequeña y una rayita encima.
        if notas_pagina:
            alto_notas = sum(len(partir(f"{n} {NOTAS[n]}", CARACTERES_NOTA)) for n in notas_pagina) * INTERLINEA_NOTA
            yn = abajo_cuerpo + alto_notas
            ordenes.append(b"%d %g m %d %g l S" % (MARGEN, yn + 6, MARGEN + 80, yn + 6))
            for n in notas_pagina:
                for l in partir(f"{n} {NOTAS[n]}", CARACTERES_NOTA):
                    ordenes.append(b"BT /F1 %g Tf %d %g Td (" % (NOTA, MARGEN, yn) + texto_pdf(l) + b") Tj ET")
                    yn -= INTERLINEA_NOTA
        paginas.append(ordenes)

    objetos = []

    def nuevo(contenido):
        objetos.append(contenido)
        return len(objetos)

    catalogo = nuevo(None)
    arbol = nuevo(None)
    f1 = nuevo(b"<< /Type /Font /Subtype /Type1 /BaseFont /Times-Roman /Encoding /WinAnsiEncoding >>")
    f2 = nuevo(b"<< /Type /Font /Subtype /Type1 /BaseFont /Times-Bold /Encoding /WinAnsiEncoding >>")
    f3 = nuevo(b"<< /Type /Font /Subtype /Type1 /BaseFont /Times-Italic /Encoding /WinAnsiEncoding >>")
    hojas = []
    for ordenes in paginas:
        flujo = b"\n".join(ordenes)
        c = nuevo(b"<< /Length %d >>\nstream\n" % len(flujo) + flujo + b"\nendstream")
        hojas.append(nuevo(
            b"<< /Type /Page /Parent %d 0 R /MediaBox [0 0 %d %d] /Contents %d 0 R "
            b"/Resources << /Font << /F1 %d 0 R /F2 %d 0 R /F3 %d 0 R >> >> >>" % (arbol, ANCHO, ALTO, c, f1, f2, f3)
        ))
    info = nuevo(b"<< /Title (" + texto_pdf("Ensayo sobre la lectura") + b") /Author (" + texto_pdf("Libertalia (prueba)") + b") >>")
    objetos[catalogo - 1] = b"<< /Type /Catalog /Pages %d 0 R >>" % arbol
    objetos[arbol - 1] = b"<< /Type /Pages /Kids [" + b" ".join(b"%d 0 R" % h for h in hojas) + b"] /Count %d >>" % len(hojas)

    salida = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    posiciones = []
    for n, obj in enumerate(objetos, start=1):
        posiciones.append(len(salida))
        salida += b"%d 0 obj\n" % n + obj + b"\nendobj\n"
    inicio_xref = len(salida)
    salida += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objetos) + 1)
    for p in posiciones:
        salida += b"%010d 00000 n \n" % p
    salida += b"trailer\n<< /Size %d /Root %d 0 R /Info %d 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (
        len(objetos) + 1, catalogo, info, inicio_xref)
    os.makedirs(SALIDA, exist_ok=True)
    ruta = os.path.join(SALIDA, "ensayo-notas.pdf")
    with open(ruta, "wb") as f:
        f.write(salida)
    print("Creado", os.path.abspath(ruta), "con", len(paginas), "páginas")


if __name__ == "__main__":
    crear()
