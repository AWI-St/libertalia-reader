"""
Crea cómics de prueba inventados (sin derechos de autor) para probar la fase 5:

- mareas-rojas-1.cbz: manga (de derecha a izquierda) con ComicInfo.xml: serie,
  tomo 1, autor. 12 páginas con viñetas de colores y el número de página.
- la-ciudad-sumergida-02.cbz: cómic sin ComicInfo (el tomo sale del nombre). 8 páginas.
- tormenta-de-papel.cbr: cómic en RAR 4 (el que sabe abrir junrar). 6 páginas.
- archivo-rar5.cbr: un RAR 5 de mentira, para ver el aviso de que no se puede abrir.
- comic-escaneado.pdf: PDF con páginas que son solo imágenes (se trata como cómic).

Las imágenes son PNG hechos a mano (sin librerías), así que el script no necesita
instalar nada.

Uso: python herramientas/crear_comics_prueba.py
"""

import os
import struct
import time
import zipfile
import zlib

SALIDA = os.path.join(os.path.dirname(__file__), "libros-prueba")
ANCHO, ALTO = 480, 720

# Dígitos de 5x7 píxeles para escribir el número de página.
DIGITOS = {
    "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    "6": ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "01111", "00001", "00010", "01100"],
}

PALETAS = [
    [(226, 214, 190), (216, 196, 162), (234, 224, 204)],
    [(200, 70, 60), (240, 200, 120), (90, 120, 160)],
    [(60, 110, 90), (230, 220, 170), (170, 90, 60)],
]


def png(pixeles, ancho, alto):
    """Convierte filas de bytes RGB en un archivo PNG."""
    crudo = b"".join(b"\x00" + bytes(fila) for fila in pixeles)

    def trozo(tipo, datos):
        return struct.pack(">I", len(datos)) + tipo + datos + struct.pack(">I", zlib.crc32(tipo + datos) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n"
            + trozo(b"IHDR", struct.pack(">IIBBBBB", ancho, alto, 8, 2, 0, 0, 0))
            + trozo(b"IDAT", zlib.compress(crudo, 9))
            + trozo(b"IEND", b""))


def pagina(numero, paleta, margen_blanco=0):
    """Una página de cómic: borde, tres viñetas de colores y el número grande abajo."""
    fondo = (250, 248, 242)
    tinta = (30, 26, 24)
    filas = [bytearray(fondo * ANCHO) for _ in range(ALTO)]

    def rect(x0, y0, x1, y1, color):
        for y in range(max(0, y0), min(ALTO, y1)):
            fila = filas[y]
            for x in range(max(0, x0), min(ANCHO, x1)):
                fila[3 * x:3 * x + 3] = bytes(color)

    m = 24 + margen_blanco
    vinetas = [
        (m, m, ANCHO - m, 260),
        (m, 276, ANCHO // 2 - 8, 500),
        (ANCHO // 2 + 8, 276, ANCHO - m, 500),
        (m, 516, ANCHO - m, ALTO - m),
    ]
    for i, (x0, y0, x1, y1) in enumerate(vinetas):
        rect(x0, y0, x1, y1, tinta)
        rect(x0 + 5, y0 + 5, x1 - 5, y1 - 5, paleta[(i + numero) % len(paleta)])
    # Número de página en la última viñeta, cada «píxel» del dígito es un cuadrado de 14.
    texto = str(numero)
    tam = 14
    x = ANCHO // 2 - len(texto) * 6 * tam // 2
    y = 560
    for d in texto:
        for fy, patron in enumerate(DIGITOS[d]):
            for fx, bit in enumerate(patron):
                if bit == "1":
                    rect(x + fx * tam, y + fy * tam, x + fx * tam + tam, y + fy * tam + tam, tinta)
        x += 6 * tam
    return png(filas, ANCHO, ALTO)


COMIC_INFO = """<?xml version="1.0" encoding="utf-8"?>
<ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <Title>El puerto de las brumas</Title>
  <Series>Mareas Rojas</Series>
  <Number>1</Number>
  <Writer>Aiko Tanabe</Writer>
  <LanguageISO>es</LanguageISO>
  <Manga>YesAndRightToLeft</Manga>
</ComicInfo>
"""


def crear_cbz(nombre, paginas, paleta, con_info):
    ruta = os.path.join(SALIDA, nombre)
    with zipfile.ZipFile(ruta, "w", zipfile.ZIP_DEFLATED) as z:
        if con_info:
            z.writestr("ComicInfo.xml", COMIC_INFO)
        # Nombres sin ceros a la izquierda a propósito: hay que ordenarlos como números.
        for n in range(1, paginas + 1):
            z.writestr(f"pagina {n}.png", pagina(n, paleta))
    return ruta


def crear_cbr(nombre, paginas, paleta):
    """RAR 4 «almacenado» (sin comprimir), que junrar sabe leer."""

    def bloque(tipo, flags, cuerpo):
        cabecera = struct.pack("<BHH", tipo, flags, 7 + len(cuerpo)) + cuerpo
        return struct.pack("<H", zlib.crc32(cabecera) & 0xFFFF) + cabecera

    datos = bytearray(b"Rar!\x1a\x07\x00")
    datos += bloque(0x73, 0x0000, struct.pack("<HI", 0, 0))
    t = time.localtime()
    fecha = ((t.tm_year - 1980) << 25) | (t.tm_mon << 21) | (t.tm_mday << 16) | (t.tm_hour << 11) | (t.tm_min << 5) | (t.tm_sec // 2)
    for n in range(1, paginas + 1):
        contenido = pagina(n, paleta, margen_blanco=30)
        nombre_archivo = f"{n:02d}.png".encode()
        cuerpo = struct.pack(
            "<IIBIIBBHI",
            len(contenido), len(contenido), 2, zlib.crc32(contenido) & 0xFFFFFFFF,
            fecha, 20, 0x30, len(nombre_archivo), 0x20,
        ) + nombre_archivo
        datos += bloque(0x74, 0x8000, cuerpo) + contenido
    datos += bloque(0x7B, 0x4000, b"")
    ruta = os.path.join(SALIDA, nombre)
    with open(ruta, "wb") as f:
        f.write(datos)
    return ruta


def crear_rar5_falso(nombre):
    ruta = os.path.join(SALIDA, nombre)
    with open(ruta, "wb") as f:
        f.write(b"Rar!\x1a\x07\x01\x00" + bytes(200))
    return ruta


def crear_pdf_imagenes(nombre, paginas, paleta):
    """PDF en el que cada página es solo una imagen (como un cómic escaneado)."""
    objetos = []

    def nuevo(contenido):
        objetos.append(contenido)
        return len(objetos)

    catalogo = nuevo(None)
    arbol = nuevo(None)
    hojas = []
    for n in range(1, paginas + 1):
        # Imagen RGB sin comprimir dentro del PDF (FlateDecode de las filas).
        filas = []
        png_bytes = pagina(n, paleta)
        # Se rehacen los píxeles en crudo a partir del PNG: más fácil volver a dibujar.
        crudo = zlib.decompress(png_bytes[png_bytes.index(b"IDAT") + 4:png_bytes.index(b"IEND") - 8])
        ancho_fila = 1 + ANCHO * 3
        pix = b"".join(crudo[i * ancho_fila + 1:(i + 1) * ancho_fila] for i in range(ALTO))
        flujo = zlib.compress(pix, 9)
        imagen = nuevo(b"<< /Type /XObject /Subtype /Image /Width %d /Height %d /ColorSpace /DeviceRGB "
                       b"/BitsPerComponent 8 /Filter /FlateDecode /Length %d >>\nstream\n" % (ANCHO, ALTO, len(flujo))
                       + flujo + b"\nendstream")
        dibujo = b"q %d 0 0 %d 0 0 cm /Im1 Do Q" % (ANCHO, ALTO)
        contenido = nuevo(b"<< /Length %d >>\nstream\n" % len(dibujo) + dibujo + b"\nendstream")
        hojas.append(nuevo(
            b"<< /Type /Page /Parent %d 0 R /MediaBox [0 0 %d %d] /Contents %d 0 R "
            b"/Resources << /XObject << /Im1 %d 0 R >> >> >>" % (arbol, ANCHO, ALTO, contenido, imagen)
        ))
    info = nuevo(b"<< /Title (Comic escaneado) /Author (Libertalia \\(prueba\\)) >>")
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
    ruta = os.path.join(SALIDA, nombre)
    with open(ruta, "wb") as f:
        f.write(salida)
    return ruta


if __name__ == "__main__":
    os.makedirs(SALIDA, exist_ok=True)
    for ruta in [
        crear_cbz("mareas-rojas-1.cbz", 12, PALETAS[0], con_info=True),
        crear_cbz("la-ciudad-sumergida-02.cbz", 8, PALETAS[1], con_info=False),
        crear_cbr("tormenta-de-papel.cbr", 6, PALETAS[2]),
        crear_rar5_falso("archivo-rar5.cbr"),
        crear_pdf_imagenes("comic-escaneado.pdf", 5, PALETAS[1]),
    ]:
        print("Creado", os.path.abspath(ruta), os.path.getsize(ruta), "bytes")
