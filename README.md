# Libertalia Reader

Lector de libros, cómics y manga para Android. Gratis, privado y propio: funciona entero en el móvil, sin cuentas, sin anuncios, sin servidores y sin conexión a internet (la app no tiene permiso de internet).

## Qué hace

- **Libros:** EPUB y PDF, con temas cálidos (crema, sepia y oscuro), tipografías (Literata, Lora, Atkinson Hyperlegible y OpenDyslexic), índice, búsqueda, marcapáginas y «modo texto» para leer cómodamente los PDF pensados para imprimir (con las notas al pie aparte).
- **Cómics y manga:** CBZ, CBR (RAR 4) y PDF de cómic; página a página, doble página y scroll vertical; lectura de derecha a izquierda; zoom y recorte de márgenes.
- **Subrayados y tarjetas:** subrayados en cuatro colores, notas, etiquetas y páginas destacadas; cada subrayado es una tarjeta con su frase completa, que se puede buscar, filtrar y exportar (Markdown, CSV y Anki).
- **Biblioteca:** estantes de madera, estados (leyendo, pendiente, terminado), estanterías propias y «Abrir con» desde otras apps (también para solo leer un archivo, sin añadirlo).
- **Copias:** exportar e importar en un archivo `.libertalia` y copia automática al sitio que se elija (el móvil o una nube instalada), sin iniciar sesión en ningún servicio.
- **Racha de lectura y estadísticas**, con widgets para la pantalla de inicio.
- **Idiomas:** castellano, gallego, euskera, catalán, inglés y francés.

## Compilar

Hace falta Android Studio (o su JDK) y el SDK de Android.

```
./gradlew :app:assembleDebug
```

La versión firmada (`assembleRelease`) necesita un archivo `keystore.properties` en la raíz con la ruta de la clave, su alias y su contraseña; no está en el repositorio.

## Licencia

El código de Libertalia Reader se publica con la licencia [Apache 2.0](LICENSE).

La app incluye librerías y tipografías de otras personas, cada una con su licencia (se pueden ver también en la app, en Ajustes → Acerca de):

- Readium Kotlin Toolkit y PDFium: BSD 3-Clause.
- PdfiumAndroid, PdfBox-Android, Jetpack (Compose, Room, Navigation, Glance, WorkManager, AppCompat), Media3 y Kotlin: Apache 2.0.
- Bouncy Castle: MIT.
- junrar: licencia UnRAR (gratuita; no permite usar su código para crear un compresor RAR).
- Literata, Lora, Atkinson Hyperlegible y OpenDyslexic: SIL Open Font License 1.1 (en `app/src/main/assets/fonts/`).

El libro de prueba de `herramientas/libros-prueba/` es *Don Quijote*, de dominio público; los cómics y el ensayo de prueba los generan los programas de `herramientas/`.
