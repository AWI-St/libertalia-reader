"""
Crea los libros de prueba de Libertalia Reader (un EPUB y un PDF)
con el principio de "Don Quijote de la Mancha", que es de dominio público.

Uso (desde la carpeta del proyecto):
    python herramientas/crear_libros_prueba.py

Los deja en herramientas/libros-prueba/. Para probarlos, cópialos al móvil o al emulador
y añádelos desde la app con "Añadir desde el móvil".
"""
import os
import textwrap
import zipfile

SALIDA = os.path.join(os.path.dirname(__file__), "libros-prueba")

TITULO = "Don Quijote de la Mancha"
AUTOR = "Miguel de Cervantes"

CAPITULOS = [
    (
        "Capítulo primero",
        "Que trata de la condición y ejercicio del famoso hidalgo don Quijote de la Mancha",
        [
            "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo de los de lanza en astillero, adarga antigua, rocín flaco y galgo corredor.",
            "Una olla de algo más vaca que carnero, salpicón las más noches, duelos y quebrantos los sábados, lentejas los viernes, algún palomino de añadidura los domingos, consumían las tres partes de su hacienda.",
            "El resto della concluían sayo de velarte, calzas de velludo para las fiestas, con sus pantuflos de lo mesmo, y los días de entresemana se honraba con su vellorí de lo más fino.",
            "Tenía en su casa una ama que pasaba de los cuarenta, y una sobrina que no llegaba a los veinte, y un mozo de campo y plaza, que así ensillaba el rocín como tomaba la podadera.",
            "Frisaba la edad de nuestro hidalgo con los cincuenta años; era de complexión recia, seco de carnes, enjuto de rostro, gran madrugador y amigo de la caza.",
            "Quieren decir que tenía el sobrenombre de Quijada, o Quesada, que en esto hay alguna diferencia en los autores que deste caso escriben; aunque por conjeturas verosímiles se deja entender que se llamaba Quejana. Pero esto importa poco a nuestro cuento; basta que en la narración dél no se salga un punto de la verdad.",
            "Es, pues, de saber, que este sobredicho hidalgo, los ratos que estaba ocioso, que eran los más del año, se daba a leer libros de caballerías con tanta afición y gusto, que olvidó casi de todo punto el ejercicio de la caza, y aun la administración de su hacienda.",
            "Y llegó a tanto su curiosidad y desatino en esto, que vendió muchas hanegas de tierra de sembradura para comprar libros de caballerías en que leer, y así, llevó a su casa todos cuantos pudo haber dellos.",
            "En resolución, él se enfrascó tanto en su lectura, que se le pasaban las noches leyendo de claro en claro, y los días de turbio en turbio; y así, del poco dormir y del mucho leer se le secó el celebro, de manera que vino a perder el juicio.",
            "Llenósele la fantasía de todo aquello que leía en los libros, así de encantamentos como de pendencias, batallas, desafíos, heridas, requiebros, amores, tormentas y disparates imposibles.",
            "En efeto, rematado ya su juicio, vino a dar en el más estraño pensamiento que jamás dio loco en el mundo; y fue que le pareció convenible y necesario, así para el aumento de su honra como para el servicio de su república, hacerse caballero andante, y irse por todo el mundo con sus armas y caballo a buscar las aventuras.",
            "Fue luego a ver su rocín, y, aunque tenía más cuartos que un real, le pareció que ni el Bucéfalo de Alejandro ni Babieca el del Cid con él se igualaban. Cuatro días se le pasaron en imaginar qué nombre le pondría, y al fin le vino a llamar Rocinante.",
            "Puesto nombre, y tan a su gusto, a su caballo, quiso ponérsele a sí mismo, y en este pensamiento duró otros ocho días, y al cabo se vino a llamar don Quijote de la Mancha, con que a su parecer declaraba muy al vivo su linaje y patria.",
        ],
    ),
    (
        "Capítulo II",
        "Que trata de la primera salida que de su tierra hizo el ingenioso don Quijote",
        [
            "Hechas, pues, estas prevenciones, no quiso aguardar más tiempo a poner en efeto su pensamiento, apretándole a ello la falta que él pensaba que hacía en el mundo su tardanza.",
            "Y así, sin dar parte a persona alguna de su intención, y sin que nadie le viese, una mañana, antes del día, que era uno de los calurosos del mes de julio, se armó de todas sus armas, subió sobre Rocinante y por la puerta falsa de un corral salió al campo.",
            "Con grandísimo contento y alborozo de ver con cuánta facilidad había dado principio a su buen deseo, caminaba nuestro flamante aventurero, hablando consigo mismo.",
            "Casi todo aquel día caminó sin acontecerle cosa que de contar fuese, de lo cual se desesperaba, porque quisiera topar luego luego con quien hacer experiencia del valor de su fuerte brazo.",
            "Anduvo todo aquel día, y, al anochecer, su rocín y él se hallaron cansados y muertos de hambre; y mirando a todas partes por ver si descubriría algún castillo o alguna majada de pastores donde recogerse, vio no lejos del camino por donde iba una venta.",
        ],
    ),
    (
        "Capítulo III",
        "Donde se cuenta la graciosa manera que tuvo don Quijote en armarse caballero",
        [
            "Y así, fatigado deste pensamiento, abrevió su venteril y limitada cena; la cual acabada, llamó al ventero, y, encerrándose con él en la caballeriza, se hincó de rodillas ante él.",
            "El ventero, que, como está dicho, era un poco socarrón y ya tenía algunos barruntos de la falta de juicio de su huésped, acabó de creerlo cuando acabó de oírle semejantes razones.",
            "Y, por tener qué reír aquella noche, determinó de seguirle el humor; y así, le dijo que andaba muy acertado en lo que deseaba y pedía.",
        ],
    ),
]


def escapar(texto):
    return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def crear_epub(ruta):
    """Un EPUB es un ZIP con una estructura fija. El archivo 'mimetype' va primero y sin comprimir."""
    with zipfile.ZipFile(ruta, "w") as z:
        z.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip", compress_type=zipfile.ZIP_STORED)
        z.writestr(
            "META-INF/container.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">\n'
            '  <rootfiles><rootfile full-path="OEBPS/libro.opf" media-type="application/oebps-package+xml"/></rootfiles>\n'
            "</container>\n",
            compress_type=zipfile.ZIP_DEFLATED,
        )
        items, spine, nav = [], [], []
        for i, (titulo, subtitulo, parrafos) in enumerate(CAPITULOS, start=1):
            nombre = f"capitulo{i}.xhtml"
            cuerpo = "\n".join(f"    <p>{escapar(p)}</p>" for p in parrafos)
            z.writestr(
                f"OEBPS/{nombre}",
                '<?xml version="1.0" encoding="UTF-8"?>\n'
                '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="es" lang="es">\n'
                f"<head><title>{escapar(titulo)}</title></head>\n"
                "<body>\n"
                f'  <section epub:type="chapter">\n    <h1>{escapar(titulo)}</h1>\n    <h2>{escapar(subtitulo)}</h2>\n{cuerpo}\n  </section>\n'
                "</body>\n</html>\n",
                compress_type=zipfile.ZIP_DEFLATED,
            )
            items.append(f'    <item id="cap{i}" href="{nombre}" media-type="application/xhtml+xml"/>')
            spine.append(f'    <itemref idref="cap{i}"/>')
            nav.append(f'      <li><a href="{nombre}">{escapar(titulo)}</a></li>')
        z.writestr(
            "OEBPS/nav.xhtml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="es" lang="es">\n'
            "<head><title>Índice</title></head>\n<body>\n"
            '  <nav epub:type="toc" id="toc">\n    <h1>Índice</h1>\n    <ol>\n' + "\n".join(nav) + "\n    </ol>\n  </nav>\n"
            "</body>\n</html>\n",
            compress_type=zipfile.ZIP_DEFLATED,
        )
        z.writestr(
            "OEBPS/libro.opf",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id-libro" xml:lang="es">\n'
            '  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">\n'
            '    <dc:identifier id="id-libro">libertalia-prueba-don-quijote</dc:identifier>\n'
            f"    <dc:title>{TITULO}</dc:title>\n"
            f"    <dc:creator>{AUTOR}</dc:creator>\n"
            "    <dc:language>es</dc:language>\n"
            '    <meta property="dcterms:modified">2026-11-19T00:00:00Z</meta>\n'
            "  </metadata>\n"
            "  <manifest>\n"
            '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
            + "\n".join(items)
            + "\n  </manifest>\n  <spine>\n"
            + "\n".join(spine)
            + "\n  </spine>\n</package>\n",
            compress_type=zipfile.ZIP_DEFLATED,
        )


def crear_pdf(ruta):
    """Un PDF sencillo de texto, escrito a mano. Página con proporciones de móvil y letra grande."""
    ancho, alto = 360, 640
    margen = 22

    def texto_pdf(t):
        # Codificación WinAnsi (cp1252) para las tildes y las eñes.
        b = t.encode("cp1252")
        return b.replace(b"\\", b"\\\\").replace(b"(", b"\\(").replace(b")", b"\\)")

    paginas = []
    for titulo, subtitulo, parrafos in CAPITULOS:
        lineas = [(b"F2", 20, titulo)]
        lineas += [(b"F1", 13, l) for l in textwrap.wrap(subtitulo, 44)]
        lineas.append(None)
        for p in parrafos:
            lineas += [(b"F1", 15, l) for l in textwrap.wrap(p, 40)]
            lineas.append(None)
        # Reparte las líneas en páginas.
        actual, y = [], alto - 40
        for linea in lineas:
            if y < 35:
                paginas.append(actual)
                actual, y = [], alto - 40
            if linea is None:
                y -= 8
                continue
            fuente, tam, t = linea
            actual.append(b"BT /" + fuente + b" %d Tf %d %d Td (" % (tam, margen, y) + texto_pdf(t) + b") Tj ET")
            y -= tam + 5
        paginas.append(actual)

    objetos = []

    def nuevo(contenido):
        objetos.append(contenido)
        return len(objetos)

    catalogo = nuevo(None)
    arbol = nuevo(None)
    f1 = nuevo(b"<< /Type /Font /Subtype /Type1 /BaseFont /Times-Roman /Encoding /WinAnsiEncoding >>")
    f2 = nuevo(b"<< /Type /Font /Subtype /Type1 /BaseFont /Times-Bold /Encoding /WinAnsiEncoding >>")
    hojas = []
    for contenido in paginas:
        flujo = b"\n".join(contenido)
        c = nuevo(b"<< /Length %d >>\nstream\n" % len(flujo) + flujo + b"\nendstream")
        hojas.append(
            nuevo(
                b"<< /Type /Page /Parent %d 0 R /MediaBox [0 0 %d %d] /Contents %d 0 R "
                b"/Resources << /Font << /F1 %d 0 R /F2 %d 0 R >> >> >>" % (arbol, ancho, alto, c, f1, f2)
            )
        )
    info = nuevo(b"<< /Title (" + texto_pdf(TITULO) + b") /Author (" + texto_pdf(AUTOR) + b") >>")
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
        len(objetos) + 1, catalogo, info, inicio_xref
    )
    with open(ruta, "wb") as f:
        f.write(salida)


if __name__ == "__main__":
    os.makedirs(SALIDA, exist_ok=True)
    crear_epub(os.path.join(SALIDA, "don-quijote.epub"))
    crear_pdf(os.path.join(SALIDA, "don-quijote.pdf"))
    print("Libros de prueba creados en", os.path.abspath(SALIDA))
