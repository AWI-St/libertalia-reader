"""
Crea los strings.xml de cada idioma a partir de las traducciones (un archivo .tsv por idioma:
«clave<TAB>texto»; los plurales, «clave#one», «clave#other»...).

Uso:  python herramientas/traducciones/generar.py

Comprueba que no falte ni sobre ninguna clave y que cada traducción tenga los mismos datos
(%1$s, %d...) que el castellano. Si algo falla, avisa y no escribe ese idioma.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
RES = RAIZ / "app" / "src" / "main" / "res"
TRADUCCIONES = Path(__file__).resolve().parent
IDIOMAS = ["en", "fr", "ca", "gl", "eu"]
MARCA = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*[sdf]|%%")


def textos_base():
    """Las claves traducibles del castellano, en orden: [(clave, tipo, texto)]."""
    salida = []
    for e in ET.parse(RES / "values" / "strings.xml").getroot():
        if e.get("translatable") == "false":
            continue
        if e.tag == "string":
            salida.append((e.get("name"), "string", "".join(e.itertext())))
        elif e.tag == "plurals":
            for it in e:
                salida.append((e.get("name") + "#" + it.get("quantity"), "plural", "".join(it.itertext())))
    return salida


def escapar(texto: str) -> str:
    texto = texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    texto = texto.replace("'", "\\'").replace('"', '\\"')
    if texto[:1] in ("@", "?"):
        texto = "\\" + texto
    return texto


def marcas(texto: str):
    return sorted(m.replace("%d", "%1$d").replace("%s", "%1$s") for m in MARCA.findall(texto))


def generar(idioma: str, base) -> bool:
    archivo = TRADUCCIONES / f"{idioma}.tsv"
    traducido = {}
    for n, linea in enumerate(archivo.read_text(encoding="utf-8").splitlines(), 1):
        if not linea.strip():
            continue
        if "\t" not in linea:
            print(f"[{idioma}] línea {n} sin tabulador: {linea[:60]}")
            return False
        clave, texto = linea.split("\t", 1)
        traducido[clave] = texto
    bien = True
    claves = {c for c, _, _ in base}
    for c in claves - traducido.keys():
        print(f"[{idioma}] falta: {c}")
        bien = False
    for c in traducido.keys() - claves:
        print(f"[{idioma}] sobra (no existe en castellano): {c}")
        bien = False
    for c, _, original in base:
        if c in traducido and marcas(original) != marcas(traducido[c]):
            print(f"[{idioma}] datos distintos en {c}: {marcas(original)} ≠ {marcas(traducido[c])}")
            bien = False
    if not bien:
        return False

    lineas = ['<?xml version="1.0" encoding="utf-8"?>',
              f"<!-- Generado con herramientas/traducciones/generar.py a partir de {idioma}.tsv: no editar a mano. -->",
              "<resources>"]
    plural_abierto = None
    for c, tipo, _ in base:
        if tipo == "string":
            if plural_abierto:
                lineas.append("    </plurals>")
                plural_abierto = None
            lineas.append(f'    <string name="{c}">{escapar(traducido[c])}</string>')
        else:
            nombre, cantidad = c.split("#")
            if plural_abierto != nombre:
                if plural_abierto:
                    lineas.append("    </plurals>")
                lineas.append(f'    <plurals name="{nombre}">')
                plural_abierto = nombre
            lineas.append(f'        <item quantity="{cantidad}">{escapar(traducido[c])}</item>')
    if plural_abierto:
        lineas.append("    </plurals>")
    lineas.append("</resources>")
    destino = RES / f"values-{idioma}"
    destino.mkdir(exist_ok=True)
    (destino / "strings.xml").write_text("\n".join(lineas) + "\n", encoding="utf-8")
    print(f"[{idioma}] {len(traducido)} textos escritos en {destino.name}/strings.xml")
    return True


if __name__ == "__main__":
    base = textos_base()
    pedidos = sys.argv[1:] or [i for i in IDIOMAS if (TRADUCCIONES / f"{i}.tsv").exists()]
    resultados = [generar(i, base) for i in pedidos]
    sys.exit(0 if all(resultados) else 1)
