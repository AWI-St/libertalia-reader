"""Dibuja el pirata de la racha de lectura (cuerpo entero, pata de palo, brújula y libro).

Para cada estado (contento, tranquilo, asustado, triste) crea 4 fotogramas que el widget
va turnando para animarlo. Escribe:
  - app/src/main/res/drawable/pirata_<estado>_<n>.xml  (vectores de Android)
  - herramientas/pirata-vista-previa.html             (para verlos en el navegador)

Uso: python herramientas/crear_pirata.py
Dibujo propio (sin licencias de terceros).
"""
import os

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESTINO = os.path.join(RAIZ, "app", "src", "main", "res", "drawable")
VISTA = os.path.join(RAIZ, "herramientas", "pirata-vista-previa.html")

ANCHO, ALTO = 120, 160

# Colores (los de la app y algunos propios del personaje).
TINTA = "3B2F2A"
PIEL = "F1CBA5"
PIEL_SOMBRA = "D9A57E"
MEJILLA = "E8907A"
SOMBRERO = "2E2420"
CREMA = "FBF6EC"
CAMISA = "F3EADB"
CASACA = "7A4A2A"
CASACA_SOMBRA = "5E3820"
ROJO = "C8322B"
ROJO_OSCURO = "9E2520"
DORADO = "F2C14E"
PANTALON = "4A3B33"
BOTA = "2B211D"
MADERA = "B07A45"
MADERA_OSCURA = "7D5229"
BARBA = "5A3A26"
LIBRO = "8C3B2E"
BOCA = "8C3B2E"
AZUL = "7FB6E6"


# --- Piezas: caminos y grupos -----------------------------------------------------------

def P(d, fill=None, stroke=None, w=1.6, a=1.0):
    """Un camino con relleno y/o trazo. a = opacidad del relleno."""
    return {"t": "p", "d": d, "fill": fill, "stroke": stroke, "w": w, "a": a}


def G(*hijos, tx=0.0, ty=0.0, rot=0.0, px=0.0, py=0.0):
    """Un grupo que se mueve (tx, ty) y gira (rot grados alrededor de px, py)."""
    return {"t": "g", "hijos": [h for h in hijos if h], "tx": tx, "ty": ty, "rot": rot, "px": px, "py": py}


def elipse(cx, cy, rx, ry=None):
    ry = rx if ry is None else ry
    return f"M{cx - rx},{cy}a{rx},{ry} 0,1 1,{2 * rx},0a{rx},{ry} 0,1 1,{-2 * rx},0Z"


def estrella(cx, cy, r):
    """Destello de cuatro puntas."""
    k = r * 0.28
    return (f"M{cx},{cy - r}L{cx + k},{cy - k}L{cx + r},{cy}L{cx + k},{cy + k}"
            f"L{cx},{cy + r}L{cx - k},{cy + k}L{cx - r},{cy}L{cx - k},{cy - k}Z")


# --- El pirata ---------------------------------------------------------------------------

def brujula(giro):
    cx, cy = 104, 60
    return G(
        P("M101,48.5h6v3h-6Z", DORADO, TINTA, 1.0),                    # anilla
        P(elipse(cx, cy, 10.5), CREMA, MARRON_BRUJULA, 2.2),
        P(elipse(cx, cy, 7.5), None, PIEL_SOMBRA, 0.6),
        G(
            P(f"M{cx},{cy - 8}L{cx + 2.8},{cy}L{cx - 2.8},{cy}Z", TINTA),  # norte: negra
            P(f"M{cx},{cy + 8}L{cx + 2.8},{cy}L{cx - 2.8},{cy}Z", ROJO),   # sur: roja
            P(elipse(cx, cy, 1.3), CREMA),
            rot=giro, px=cx, py=cy,
        ),
    )


MARRON_BRUJULA = CASACA


def piernas():
    return [
        # Cadera (asoma entre los faldones de la casaca)
        P("M45,96L75,96L77,111L43,111Z", PANTALON, TINTA),
        # Pierna con bota
        P("M45,108L58,108L57,136L46,136Z", PANTALON, TINTA),
        P("M44,138L58,138L58,146Q66,146 67,150L67,152L44,152Z", BOTA, TINTA),
        P("M43,134L59,134L59,139L43,139Z", "3A2C26", TINTA, 1.2),
        # Pierna con pata de palo (pantalón remangado)
        P("M62,108L76,108L75,127L63,127Z", PANTALON, TINTA),
        P("M61.5,125L76.5,125L76.5,131L61.5,131Z", CAMISA, TINTA, 1.2),
        P("M66,131L72,131L70.6,151L67.4,151Z", MADERA, MADERA_OSCURA, 1.2),
        P("M65.5,131h7v2.6h-7Z", MADERA_OSCURA),
        P("M67.6,136L68.4,147", None, MADERA_OSCURA, 0.7),
    ]


def torso():
    return [
        # Casaca larga con faldones
        P("M39,66Q60,59 81,66L88,118Q78,121 70,116L60,93L50,116Q42,121 32,118Z", CASACA, TINTA),
        # Camisa
        P("M52,63L60,91L68,63Q60,61 52,63Z", CAMISA, TINTA, 1.2),
        P("M57,72Q60,74 63,72M58,80Q60,81.5 62,80", None, PIEL_SOMBRA, 0.8),
        # Solapas
        P("M52,63L58,91", None, CASACA_SOMBRA, 1.3),
        P("M68,63L62,91", None, CASACA_SOMBRA, 1.3),
        # Botones dorados
        P(elipse(47.5, 73, 1.6), DORADO, TINTA, 0.6),
        P(elipse(48.5, 81, 1.6), DORADO, TINTA, 0.6),
        P(elipse(72.5, 73, 1.6), DORADO, TINTA, 0.6),
        P(elipse(71.5, 81, 1.6), DORADO, TINTA, 0.6),
        # Fajín rojo con el nudo colgando
        P("M41,89Q60,94 79,89L80,98Q60,103 40,98Z", ROJO, TINTA, 1.2),
        P("M73,96L80,111L75.5,112L70,98Z", ROJO_OSCURO, TINTA, 1.0),
    ]


def brazo_libro():
    """Brazo izquierdo (a la izquierda del dibujo) con un libro en la mano."""
    return [
        P("M41,67Q30,74 29,88", None, TINTA, 10.6),
        P("M41,67Q30,74 29,88", None, CASACA, 8.4),
        P(elipse(29, 89, 4.2, 2.4), CAMISA, TINTA, 1.0),
        # Libro (tapa roja, cantos de las hojas)
        G(
            P("M20,88L36,88L36,100L20,100Z", LIBRO, TINTA, 1.2),
            P("M21.5,89.5L34.5,89.5", None, CREMA, 0.9),
            P("M28,88L28,100", None, TINTA, 0.9),
            P("M23,94h3M30,94h3", None, DORADO, 0.9),
            rot=-12, px=28, py=94,
        ),
        P(elipse(30, 92, 3.8), PIEL, TINTA, 1.2),
    ]


def brazo_brujula(alto=0.0):
    """Brazo derecho, levantado, con la brújula en la mano. alto: cuánto sube (negativo) o baja."""
    codo = f"M79,68Q90,74 92,{86 + alto * 0.5}"
    mano = f"M92,{86 + alto * 0.5}Q99,{82 + alto} 101,{73 + alto}"
    return [
        P(codo, None, TINTA, 10.6), P(mano, None, TINTA, 9.6),
        P(codo, None, CASACA, 8.4), P(mano, None, CASACA, 7.4),
        P(elipse(101, 72 + alto, 4.2, 2.4), CAMISA, TINTA, 1.0),
    ]


def cabeza(cara, inclinacion=0.0):
    """Cabeza con tricornio, parche, bigote y perilla. cara: piezas de la expresión."""
    return G(
        # Cuello y orejas
        P("M55,56h10v8h-10Z", PIEL_SOMBRA),
        P(elipse(45.5, 45, 3, 3.6), PIEL, TINTA, 1.2),
        P(elipse(74.5, 45, 3, 3.6), PIEL, TINTA, 1.2),
        P(elipse(45, 51, 2), None, DORADO, 1.1),                       # pendiente
        # Cara
        P(elipse(60, 44, 15, 15.5), PIEL, TINTA),
        # Pelo bajo el sombrero
        P("M45.5,33Q42,42 46,49L49,39Z", BARBA, TINTA, 0.8),
        P("M74.5,33Q78,42 74,49L71,39Z", BARBA, TINTA, 0.8),
        # Nariz
        P(elipse(60, 48, 2.2, 1.8), PIEL_SOMBRA),
        # Bigote y perilla
        P("M52,52Q56,49 60,51Q64,49 68,52Q64,54.5 60,53Q56,54.5 52,52Z", BARBA, TINTA, 0.8),
        P("M56.5,59.5Q60,67 63.5,59.5Q60,61 56.5,59.5Z", BARBA, TINTA, 0.8),
        # Parche en el ojo y su cinta
        P("M45,35L75,48", None, TINTA, 1.4),
        P(elipse(54.5, 42, 4.6, 4.1), TINTA),
        # Expresión
        *cara,
        # Tricornio con calavera y ribete dorado
        P("M44,33Q45,15 60,14Q75,15 76,33Z", SOMBRERO, TINTA, 1.2),
        P("M32,35Q35,22 46,28Q60,21 74,28Q85,22 88,35Q60,27 32,35Z", SOMBRERO, TINTA, 1.2),
        P("M33,34.4Q60,26.6 87,34.4", None, DORADO, 1.1),
        P(elipse(60, 22, 3.6, 3.3), CREMA),
        P("M57.8,24h4.4v2.6h-4.4Z", CREMA),
        P(elipse(58.6, 21.8, 0.8) + elipse(61.4, 21.8, 0.8), SOMBRERO),
        P("M55,28.2L65,31.4M65,28.2L55,31.4", None, CREMA, 1.3),
        rot=inclinacion, px=60, py=60,
    )


def pirata(cara, giro=0.0, dx=0.0, dy=0.0, inclinacion=0.0, alto_brazo=0.0, extra=()):
    figura = G(
        *piernas(), *torso(), *brazo_libro(), *brazo_brujula(alto_brazo),
        brujula_en(alto_brazo, giro),
        cabeza(cara, inclinacion),
        *extra,
        tx=dx, ty=dy,
    )
    # Sombra en el suelo (no se mueve; encoge si el pirata salta)
    sombra = P(elipse(56, 153, 26 + dy, 3.2), "000000", a=0.16)
    return [sombra, figura]


def brujula_en(alto, giro):
    return G(brujula(giro), ty=alto)


# --- Expresiones --------------------------------------------------------------------------

def ojo_abierto(dy=0.0):
    return [
        P(elipse(66, 42.5 + dy, 1.9, 2.2), TINTA),
        P(elipse(66.6, 41.8 + dy, 0.6), CREMA),
    ]


def ojo_cerrado():
    return [P("M63.5,42.6L68.5,42.6", None, TINTA, 1.4)]


def mejillas():
    return [P(elipse(68, 49, 2.6, 1.8), MEJILLA, a=0.55), P(elipse(51, 49.5, 2.4, 1.7), MEJILLA, a=0.45)]


def contento(guino):
    ojo = [P("M63,43.5Q66,39.5 69,43.5", None, TINTA, 1.5)] if guino else ojo_abierto()
    return ojo + mejillas() + [
        P("M63.5,37.6Q66,35.8 68.8,37.2", None, BARBA, 1.2),
        P("M54.5,55Q60,61.5 65.5,55Q60,57 54.5,55Z", BOCA, TINTA, 1.1),
    ]


def tranquilo(parpadeo):
    ojo = ojo_cerrado() if parpadeo else ojo_abierto()
    return ojo + [
        P("M63.5,38.4Q66,37.2 68.8,38.2", None, BARBA, 1.2),
        P("M56.5,56Q60,58.2 63.5,56", None, TINTA, 1.2),
    ]


def asustado():
    return [
        P(elipse(66, 42.5, 3.4, 3.6), CREMA, TINTA, 1.1),
        P(elipse(66, 43, 1.5), TINTA),
        P("M62.6,35.2Q66,32.6 69.6,34.6", None, BARBA, 1.3),
        P(elipse(60, 57, 2.2, 2.6), BOCA, TINTA, 1.1),
    ]


def triste():
    return [
        P("M63.5,42.2Q66,44.6 68.5,42.2", None, TINTA, 1.4),
        P("M63.4,36.8L69,39", None, BARBA, 1.3),
        P("M55.5,58Q60,54.2 64.5,58", None, TINTA, 1.3),
    ]


def gota(x, y, s=1.0, a=1.0):
    return P(f"M{x},{y}Q{x + 2.6 * s},{y + 4 * s} {x + 1.6 * s},{y + 5.6 * s}"
             f"Q{x},{y + 6.8 * s} {x - 1.6 * s},{y + 5.6 * s}Q{x - 2.6 * s},{y + 4 * s} {x},{y}Z",
             AZUL, TINTA, 0.6, a)


# --- Los fotogramas de cada estado ------------------------------------------------------

def fotogramas():
    brillo = lambda r1, r2: [P(estrella(117, 46, r1), DORADO), P(estrella(90, 44, r2), DORADO)]
    return {
        # Contento: da saltitos, guiña el ojo y brillan los destellos.
        "contento": [
            pirata(contento(True), giro=0, extra=brillo(4, 2.6)),
            pirata(contento(True), giro=10, dy=-3, alto_brazo=-2, extra=brillo(5.5, 3.4)),
            pirata(contento(False), giro=0, extra=brillo(3.4, 2.2)),
            pirata(contento(True), giro=-10, dy=-3, alto_brazo=-2, extra=brillo(5, 3.8)),
        ],
        # Tranquilo: respira, parpadea y la aguja se mece.
        "tranquilo": [
            pirata(tranquilo(False), giro=-15),
            pirata(tranquilo(False), giro=-6, dy=-0.8),
            pirata(tranquilo(True), giro=-15),
            pirata(tranquilo(False), giro=-24, dy=-0.8),
        ],
        # Asustado: tiembla, suda y la aguja da vueltas.
        "asustado": [
            pirata(asustado(), giro=40, dx=-1.2, extra=[gota(76, 33)]),
            pirata(asustado(), giro=95, dx=1.2, alto_brazo=1, extra=[gota(76.6, 35)]),
            pirata(asustado(), giro=10, dx=-1.2, extra=[gota(77.2, 37, 0.9)]),
            pirata(asustado(), giro=70, dx=1.2, alto_brazo=1, extra=[gota(76, 33)]),
        ],
        # Triste: cabeza gacha, cae una lágrima y la aguja apunta al suelo.
        "triste": [
            pirata(triste(), giro=180, inclinacion=7, alto_brazo=6, extra=[gota(67.5, 45, 0.8)]),
            pirata(triste(), giro=172, inclinacion=7, alto_brazo=6, dy=0.6, extra=[gota(67.8, 48.5, 0.8)]),
            pirata(triste(), giro=180, inclinacion=7, alto_brazo=6, extra=[gota(68.1, 52, 0.8, 0.7)]),
            pirata(triste(), giro=188, inclinacion=7, alto_brazo=6, dy=0.6),
        ],
    }


# --- Escribir SVG y vectores de Android ------------------------------------------------

def a_svg(e):
    if e["t"] == "g":
        t = []
        if e["tx"] or e["ty"]:
            t.append(f"translate({e['tx']},{e['ty']})")
        if e["rot"]:
            t.append(f"rotate({e['rot']},{e['px']},{e['py']})")
        attr = f' transform="{" ".join(t)}"' if t else ""
        return f"<g{attr}>" + "".join(a_svg(h) for h in e["hijos"]) + "</g>"
    fill = f'fill="#{e["fill"]}" fill-opacity="{e["a"]}"' if e["fill"] else 'fill="none"'
    stroke = (f' stroke="#{e["stroke"]}" stroke-width="{e["w"]}" stroke-linecap="round" stroke-linejoin="round"'
              if e["stroke"] else "")
    return f'<path d="{e["d"]}" {fill}{stroke}/>'


def a_android(e, sangria="    "):
    if e["t"] == "g":
        attrs = []
        if e["tx"]:
            attrs.append(f'android:translateX="{e["tx"]}"')
        if e["ty"]:
            attrs.append(f'android:translateY="{e["ty"]}"')
        if e["rot"]:
            attrs += [f'android:rotation="{e["rot"]}"', f'android:pivotX="{e["px"]}"', f'android:pivotY="{e["py"]}"']
        cab = f"{sangria}<group" + "".join(f"\n{sangria}    {a}" for a in attrs) + ">\n"
        return cab + "".join(a_android(h, sangria + "    ") for h in e["hijos"]) + f"{sangria}</group>\n"
    attrs = [f'android:pathData="{e["d"]}"']
    if e["fill"]:
        alfa = format(round(e["a"] * 255), "02X")
        attrs.append(f'android:fillColor="#{alfa}{e["fill"]}"')
    if e["stroke"]:
        attrs += [f'android:strokeColor="#FF{e["stroke"]}"', f'android:strokeWidth="{e["w"]}"',
                  'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
    return f"{sangria}<path\n" + "".join(f"{sangria}    {a}\n" for a in attrs[:-1]) + f"{sangria}    {attrs[-1]} />\n"


def main():
    todos = fotogramas()
    filas = []
    for estado, cuadros in todos.items():
        svgs = []
        for n, piezas in enumerate(cuadros, start=1):
            xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
                   f"<!-- Pirata de la racha ({estado}, fotograma {n}). Generado por herramientas/crear_pirata.py -->\n"
                   '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                   f'    android:width="{ANCHO}dp"\n    android:height="{ALTO}dp"\n'
                   f'    android:viewportWidth="{ANCHO}"\n    android:viewportHeight="{ALTO}">\n'
                   + "".join(a_android(p) for p in piezas) + "</vector>\n")
            with open(os.path.join(DESTINO, f"pirata_{estado}_{n}.xml"), "w", encoding="utf-8", newline="\n") as f:
                f.write(xml)
            svgs.append(f'<svg viewBox="0 0 {ANCHO} {ALTO}" width="240" height="320">'
                        + "".join(a_svg(p) for p in piezas) + "</svg>")
        filas.append(f"<h2>{estado}</h2><div class='fila'>" + "".join(svgs)
                     + f"<div class='anim' data-n='{len(svgs)}'>" + "".join(svgs) + "</div></div>")
    html = """<!doctype html><meta charset="utf-8"><title>Pirata</title>
<style>body{background:#F3EADB;font-family:serif;color:#3B2F2A;margin:16px}
.fila{display:flex;gap:8px;flex-wrap:wrap;align-items:end}svg{background:#FBF6EC;border-radius:16px}
.anim{position:relative;width:240px;height:320px;outline:3px solid #7A4A2A;border-radius:16px}
.anim svg{position:absolute;left:0;top:0;visibility:hidden}</style>
""" + "".join(filas) + """
<script>document.querySelectorAll('.anim').forEach(a=>{const s=a.querySelectorAll('svg');let i=0;
s[0].style.visibility='visible';setInterval(()=>{s[i].style.visibility='hidden';i=(i+1)%s.length;
s[i].style.visibility='visible'},500)})</script>"""
    with open(VISTA, "w", encoding="utf-8") as f:
        f.write(html)
    print("ok:", sum(len(c) for c in todos.values()), "fotogramas")


if __name__ == "__main__":
    main()
