Soy principiante en programación. Quiero crear Libertalia Reader, una app Android nativa (APK) para leer libros, cómics y manga en el móvil. Explícame cada paso con palabras sencillas y dime cómo probarlo en el emulador o en mi móvil.

Filosofía: gratis, privada y propia. Funciona entera en el móvil: sin cuentas, sin registro, sin servidores, sin publicidad, sin iniciar sesión en ningún servicio y sin recoger datos de nadie. Todo se guarda en el propio dispositivo, y para cambiar de móvil se usan copias de seguridad que el usuario guarda donde quiera.

Nombre e identidad:
- Nombre de la app: Libertalia Reader (igual en todos los idiomas; no se traduce).
- Nombre de paquete: pregúntame cuál usar antes de crear el proyecto; propón io.github.<mi-usuario>.libertalia.
- Extensión de las copias de seguridad: .libertalia
- Carpeta en el móvil: Documentos/Libertalia
- Icono: bocetos/icono-libertalia.svg (una brújula cuya aguja norte es un marcapáginas pirata negro con calavera y tibias, y la aguja sur es roja, sobre fondo crema).

Tecnología:
- Kotlin con Jetpack Compose para la interfaz, y Jetpack Glance para los widgets de la pantalla de inicio.
- Readium Kotlin Toolkit para leer EPUB, PDF y CBZ, guardar posiciones, mostrar subrayados y leer en voz alta.
- Una librería libre para abrir CBR (por ejemplo junrar). Si un archivo usa un formato RAR que no se puede abrir, avisar con claridad.
- Room (SQLite) para todos los datos.
- WorkManager para las copias automáticas.
- Storage Access Framework de Android para guardar copias en la ubicación que elija el usuario (móvil, Google Drive u otras nubes instaladas), sin integrar ninguna API de nube ni inicio de sesión.
- Desactiva la copia de seguridad automática de Android (android:allowBackup="false"), para que no se suba nada a ninguna cuenta.
- Sin analíticas ni librerías de seguimiento. El único uso de internet es opcional: buscar el número de páginas de un libro por ISBN en Open Library.
- Usa solo librerías y tipografías gratuitas con licencias que permitan incluirlas en la app (por ejemplo, tipografías con licencia SIL Open Font License). Avísame si alguna dependencia tiene una licencia que lo impida.

Idiomas:
- La app está disponible en castellano, gallego, euskera, catalán, inglés y francés.
- Todos los textos de la interfaz van en recursos de Android (strings.xml), nunca escritos directamente en el código, con una carpeta por idioma (values, values-gl, values-eu, values-ca, values-en, values-fr). El castellano es el idioma base.
- Usar plurales de Android (plurals) para textos con cantidades ("1 tarjeta", "18 tarjetas").
- Por defecto, la app usa el idioma del móvil si es uno de los seis; si no, castellano.
- Selector de idioma por app en Ajustes → Idioma (con AppCompatDelegate.setApplicationLocales y locales_config.xml, para que en Android 13 o posterior aparezca también en los ajustes de idioma del sistema). Cambiar el idioma no cambia el del móvil.
- Fechas, números y tamaños de archivo con el formato del idioma elegido.
- Se traducen también los widgets, los avisos, las notificaciones y los textos del Markdown exportado. Los libros y las tarjetas del usuario no se traducen.
- Prepara las traducciones a los seis idiomas y avísame de que conviene que una persona nativa revise gallego, euskera y catalán.

Antes de escribir código, crea un archivo CLAUDE.md con esta descripción y proponme un plan por fases. Espera a que lo apruebe.

Diseño:
- Las pantallas de referencia y el icono están en la carpeta bocetos/. Síguelos.
- Estilo papel cálido, como una estantería con muchos libros: tonos crema y marrón, nunca blanco brillante.
- Colores: fondo de la app #F3EADB, fondo de lectura y de tarjetas #F8F1E3 / #FBF6EC, texto #3B2F2A, texto secundario #6E5A4B, bordes #E4D6C1, color principal marrón #7A4A2A, madera de los estantes #6B4429, rojo de acento #C8322B.
- Tipografía serif Literata en toda la app, que el usuario puede cambiar en ajustes.
- Los textos de la interfaz deben caber en todos los idiomas: nada de anchos fijos para el texto, permitir que botones y etiquetas crezcan o pasen a dos líneas (el euskera, el francés y el gallego suelen ser más largos que el castellano).
- No hay barra de navegación inferior. La pantalla principal es la Biblioteca; arriba tiene los botones de ajustes, buscar y añadir.
- Biblioteca: libros, cómics y manga en los mismos estantes de madera, agrupados por estado o estantería. Los libros se muestran de pie como libros; los cómics y mangas se muestran de frente, como revistas, con su portada visible, borde grueso y una etiqueta "CÓMIC" o "MANGA" con el número de tomo, para distinguirlos de un vistazo. Arriba, la tarjeta "Seguir leyendo"; debajo, el botón Tarjetas/Libros y un bloque pequeño con la tarjeta del día. Si no hay nada, se muestra la pantalla de biblioteca vacía con el nombre "Libertalia Reader", el mensaje "Añade libros, cómics o manga. Sin anuncios, sin cuentas, todo en tu móvil." y los botones "Añadir desde el móvil" e "Importar una copia".
- Tarjetas: dos por fila, ordenadas por página del libro. No hay una lista con todas las tarjetas mezcladas.
- Navegación: el botón y el gesto de volver regresan siempre a la pantalla desde la que se entró, conservando su estado (por ejemplo, el modo Tarjetas de la biblioteca).
- Iconos de línea, sin emojis. Zonas táctiles de al menos 44 px.

Funciones:
1. Almacenamiento:
   - Libros y cómics se guardan en la carpeta visible Documentos/Libertalia (configurable en ajustes) para que no se pierdan al desinstalar la app.
   - Cada libro o cómic tiene un identificador único, un tipo (libro de texto o cómic) y una huella de su archivo (SHA-256 del contenido), que sirve para reconocerlo aunque cambie de nombre.
   - Posiciones de lectura, marcadores, subrayados, páginas destacadas, tarjetas, notas, estados, estanterías, etiquetas, libros incluidos en la tarjeta del día y si cada elemento se incluye en la copia automática se guardan en Room, relacionados con el identificador de su libro o cómic.
   - Todo funciona sin conexión.

2. Biblioteca:
   - Formatos de texto: EPUB y PDF. Formatos de cómic: CBZ, CBR y PDF de cómic (un PDF cuyas páginas son solo imágenes se trata como cómic, con opción de cambiarlo en el menú del libro). Más adelante: TXT, CB7 y carpetas de imágenes.
   - Importar desde el almacenamiento del móvil y con "Abrir con" desde otras apps (gestor de archivos, WhatsApp, navegador).
   - Estados: leyendo, pendiente, terminado.
   - Estanterías o colecciones creadas por el usuario; un libro o cómic puede estar en varias.
   - Dos modos con un botón que alterna entre ellos:
     · Modo Libros (por defecto): el botón dice "Tarjetas" y al tocar un libro o cómic se abre para leer.
     · Modo Tarjetas: la misma estantería, el título pasa a "Tarjetas", cada libro muestra cuántas tarjetas tiene, aparece el aviso "Toca un libro para ver sus tarjetas", y al tocar un libro se abren sus tarjetas. Los cómics aparecen atenuados y no se pueden tocar. El botón dice "Libros" para volver.
     · Al volver desde las tarjetas de un libro, la biblioteca sigue en modo Tarjetas.
   - Al mantener pulsado un libro o cómic, menú con: incluir en la tarjeta del día (interruptor, solo libros), incluir el archivo en la copia automática (interruptor), ver tarjetas (solo libros), editar título, autor y portada, cambiar estado, añadir a estantería, exportar, y eliminar. En cómics también: sentido de lectura y tratar como libro o como cómic (para PDF).

3. Editar datos: el usuario puede cambiar el título, el autor, el número de tomo (en cómics) y la portada, porque muchos archivos descargados traen títulos raros o incorrectos. Al cambiar el título, ofrecer también renombrar el archivo con ese nombre en la carpeta de la app. Los cambios no modifican el contenido del archivo.

4. Lector de libros (EPUB y PDF de texto):
   - El fondo de lectura nunca es blanco brillante. Tema por defecto: crema (fondo #F8F1E3, texto #3B2F2A). Otros temas: sepia (#EAD9BC) y oscuro (fondo #2A2522 con texto #E6D9C6, sin blanco puro).
   - En PDF, aplicar por defecto un filtro cálido sobre la página para que el blanco del papel se vea crema, con opción de desactivarlo.
   - Tamaño de letra, tipografía (Literata, Lora, una sans libre y OpenDyslexic para dislexia, todas incluidas en la app), márgenes e interlineado ajustables (en EPUB), en un panel de ajustes que sube desde abajo con el botón Aa.
   - Índice del libro para saltar entre capítulos.
   - Buscar texto dentro del libro.
   - Pasar página con los botones de volumen (activable en ajustes).
   - Mantener la pantalla encendida mientras se lee y brillo ajustable dentro de la app.
   - Tiempo restante del capítulo y del libro, calculado según la velocidad de lectura del usuario.
   - Al seleccionar una palabra o frase, menú flotante con: colores de subrayado, añadir nota, diccionario, traducir y copiar. Diccionario y traducción usan las apps ya instaladas en Android (diccionario del sistema, Google Traductor) mediante las opciones de texto del sistema.
   - Barra superior: volver, título y autor, buscar y marcar página.
   - Barra inferior: progreso, Índice, Tarjetas (de este libro), Escuchar y Ajustes.
   - La barra inferior se puede ocultar con una pestaña discreta con una flecha hacia abajo en su borde superior. Al ocultarla, queda solo una pestaña pequeña anclada abajo en el centro con la flecha hacia arriba para volver a mostrarla. La app recuerda si estaba oculta.
   - Un toque en el centro muestra u oculta las barras.

5. Lector de cómics y manga (CBZ, CBR y PDF de cómic):
   - Fondo oscuro alrededor de la página por defecto (#1E1A18), configurable.
   - Modos de lectura: página a página, doble página (automática al girar el móvil en horizontal, o forzada) y scroll vertical continuo (para webtoons y manhwas).
   - Sentido de lectura: de izquierda a derecha o de derecha a izquierda (manga). Detectarlo automáticamente si el archivo incluye ComicInfo.xml con esa información; si no, elegirlo por cómic, y recordarlo. La barra de progreso y el paso de página siguen el sentido elegido.
   - Zoom con doble toque y pellizco, y desplazamiento por la página ampliada.
   - Recortar automáticamente los márgenes blancos de los escaneos (activable).
   - Pasar página con toques en los laterales, deslizando o con los botones de volumen.
   - Barra superior: volver, título y tomo, sentido de lectura y marcar página. Barra inferior: "Pág. X de Y", barra de progreso y botones Página, Doble, Scroll y sentido de lectura. Un toque en el centro las muestra u oculta.
   - Guardar automáticamente la página por la que se va y permitir marcadores de página para volver a ellas, con una lista de marcadores.
   - Los cómics no tienen subrayados, tarjetas ni tarjeta del día.

6. Posición: guardar automáticamente la posición exacta (Readium en libros, página en cómics) al salir o cerrar la app, y volver a ella al abrir.

7. Número de página en libros: en EPUB las páginas de pantalla no coinciden con las del libro impreso. Mostrar siempre capítulo y porcentaje, y además:
   - Si el EPUB incluye la lista de páginas de la edición impresa (page-list), mostrar la página real: "p. 123".
   - Si no la incluye, permitir indicar el número de páginas de la edición impresa (o buscarlo por ISBN en Open Library, si hay conexión) y mostrar una página estimada según el porcentaje: "≈ p. 123".
   - En PDF, usar las páginas reales del archivo.
   - La página es solo informativa; para volver a un sitio se usa siempre la posición exacta de Readium.

8. Subrayar texto en varios colores y marcar páginas como destacadas (solo libros de texto).

9. Tarjetas (solo libros de texto): cada subrayado o página destacada crea una tarjeta separada. Las tarjetas pertenecen a su libro: cada libro tiene su propia pantalla de tarjetas, a la que se entra desde el modo Tarjetas de la biblioteca, desde el lector o desde el menú al mantener pulsado el libro. Volver desde ella regresa a la pantalla de origen (biblioteca en modo Tarjetas, lector o biblioteca). Se muestran dos por fila, ordenadas por página.
   Cada tarjeta tiene su propio identificador único y guarda su propio texto completo (no solo una referencia al libro), para que pueda verse aunque el archivo del libro no esté:
   - el texto subrayado,
   - el contexto: la frase completa donde está el subrayado, desde el punto anterior hasta el punto siguiente, con la parte subrayada resaltada en su color,
   - el título y autor del libro tal como los ha editado el usuario (si los cambia después, todas sus tarjetas se actualizan solas), capítulo, página (real o estimada) y porcentaje,
   - la posición exacta de Readium,
   - una nota mía opcional,
   - etiquetas libres (por ejemplo, "productividad"),
   - fecha de creación y de última edición.
   Pantalla de tarjeta abierta: libro, capítulo y página; la frase con el subrayado resaltado; cambiar color y editar el texto; nota editable; etiquetas; fechas; flechas para ir a la tarjeta anterior y siguiente; botón "Ir al libro"; compartir y eliminar.
   Las tarjetas se pueden filtrar por color, tipo (subrayado o página destacada) y etiqueta.
   Búsqueda doble:
   - dentro de las tarjetas de un libro, desde su pantalla de tarjetas,
   - en las tarjetas de todos los libros, desde el buscador de la biblioteca, con los resultados agrupados por libro.

10. Tarjeta del día (solo libros de texto):
   - Cada día se elige al azar una tarjeta de entre los libros incluidos. Por defecto todos los libros están incluidos; el usuario quita o vuelve a poner un libro con el interruptor del menú que sale al mantenerlo pulsado.
   - Se muestra en un bloque pequeño en la biblioteca. Al tocarlo se abre esa tarjeta.
   - Widgets para la pantalla de inicio de Android (con Jetpack Glance), con el estilo crema de la app:
     · Widget grande: la tarjeta del día con la cita, el libro, el autor y la página, y un botón para pedir otra tarjeta.
     · Widget pequeño: la cita corta con libro y página.
     · Widget pequeño "Seguir leyendo": el libro o cómic actual con su progreso (y en libros, el tiempo para acabar el capítulo).
     Al tocar un widget se abre la tarjeta, el libro o el cómic en la app. Los widgets se actualizan una vez al día y cuando cambian los datos.

11. Exportar, importar y copias (objetivo principal: no perder nada al cambiar de móvil):
   - Pantalla Exportar, accesible desde Ajustes, y desde "Exportar" en el menú al mantener pulsado un libro o cómic (en ese caso, solo ese viene marcado):
     · Interruptor "Incluir los archivos": activado (por defecto) hace la copia completa para cambiar de móvil; desactivado exporta solo datos (posiciones, marcadores, notas y tarjetas), mucho más ligero.
     · Casilla "Seleccionar todos" arriba de todo (con estado intermedio si solo hay algunos marcados) y debajo la lista de libros y cómics, cada uno con su casilla, portada, título, número de tarjetas (en libros) y peso.
     · Abajo, el resumen de lo seleccionado (elementos, tarjetas y peso total) y dos botones:
       "Guardar en…": abre el selector de archivos de Android (Storage Access Framework) para guardar en el móvil, en Google Drive o en cualquier otra nube instalada.
       "Compartir": abre el menú de compartir de Android (WhatsApp, Telegram, correo, etc.).
     · Si el archivo supera unos 25 MB, avisar de que puede ser demasiado grande para el correo y sugerir "Guardar en…" o compartir por WhatsApp o Telegram.
     · Todo lo seleccionado se comprime en un único archivo.
   - Formato del archivo: un ZIP con la extensión propia ".libertalia", registrada en Android para que se abra con la app desde WhatsApp, Drive, el gestor de archivos o "Abrir con". Estructura:
     · manifiesto.json: versión del formato, versión de la app, fecha y lista de libros y cómics.
     · libros/<nombre-corto-e-id>/ con el archivo original (libro.epub, .pdf, .cbz o .cbr, solo si se incluyen los archivos), portada.jpg y datos.json con: identificador, tipo y huella, título, autor y tomo editados, estado, estanterías, posición, marcadores, sentido de lectura (cómics), si está incluido en la tarjeta del día y en la copia automática, páginas destacadas y todas sus tarjetas con todos sus campos.
     · legible/<Título del libro>.md: las tarjetas de cada libro en Markdown, para leerlas fuera de la app.
   - Importar: desde "Importar una copia" (biblioteca vacía o ajustes) o abriendo un archivo .libertalia. La app lo lee y muestra un resumen antes de aplicar nada (elementos nuevos, ya existentes, tarjetas nuevas que se añadirán). Al confirmar:
     · reconoce los libros y cómics que ya existen por su huella, aunque tengan otro nombre;
     · une las tarjetas y marcadores sin duplicar, usando su identificador; si existen en ambos sitios con cambios distintos, se queda con la edición más reciente;
     · si llegan datos de un libro o cómic cuyo archivo no está, lo crea "sin archivo" con sus datos, y lo completa cuando el usuario añada el archivo correspondiente (reconocido por la huella).
   - Copia automática (sin iniciar sesión en ningún servicio; la subida a la nube la hace la app de esa nube instalada en el móvil):
     · El usuario elige una sola vez dónde guardarla con el selector de Android (ACTION_CREATE_DOCUMENT), por ejemplo una carpeta de Google Drive, otra nube o el propio móvil. La app guarda el permiso permanente sobre ese archivo (takePersistableUriPermission).
     · Es un único archivo "copia.libertalia" con toda la biblioteca, que se sobrescribe en cada copia. Incluye todos los datos de todos los elementos, y los archivos de los que tengan activado "incluir en la copia automática".
     · Al añadir un archivo que pesa mucho (por defecto más de 50 MB, configurable; lo habitual en cómics), mostrar un aviso con su título y peso preguntando si incluirlo en la copia automática: "Incluir en la copia" o "Solo la página y los marcadores", con la casilla "Recordar mi elección para los próximos". La elección se puede cambiar después en el menú al mantener pulsado.
     · Frecuencia elegible por el usuario: cada 1 día, 2 días, 3 días, 7 días, 15 días, 1 mes o nunca. Por defecto, cada 3 días.
     · Si no ha cambiado nada desde la última copia correcta, no se rehace ni se sobrescribe el archivo.
     · Programarla con WorkManager para que se haga en segundo plano, preferiblemente con el móvil cargando o con batería suficiente, y nunca mientras se está leyendo.
     · Escribir primero la copia completa y solo después sustituir el archivo anterior, para no dejar nunca una copia rota si algo falla a mitad.
     · Botón "Hacer copia ahora" para forzarla en cualquier momento.
     · Si se pierde el permiso o la ubicación ya no existe, avisar al usuario y pedirle que elija de nuevo el sitio.
     · Mostrar en Ajustes la ubicación elegida, la frecuencia, el peso aproximado de la copia y la fecha de la última copia correcta.
   - Exportar las tarjetas de un libro también por separado a Markdown, CSV y Anki, usando el título editado del libro.

12. Pantalla Ajustes (desde el icono de ajustes de la biblioteca):
   - Aviso de que todo se guarda solo en este móvil y de que conviene tener activada la copia automática.
   - Estadísticas rápidas: racha de días leyendo, libros terminados este año y tiempo leído esta semana.
   - Bloque "Copias y cambio de móvil": Exportar, Importar una copia y Copia automática (ubicación, frecuencia, peso aproximado, fecha de la última y "Hacer copia ahora").
   - Ajustes: idioma (castellano, gallego, euskera, catalán, inglés, francés, o el del sistema), tipografía de la app, carpeta de la app, límite de peso para el aviso de archivos pesados, ajustes por defecto del lector de cómics y estadísticas completas.
   - Al final, "Acerca de Libertalia Reader": icono, versión, un texto breve sobre la filosofía de la app (gratis, sin anuncios, sin cuentas, todo en tu móvil) y la lista de licencias de las librerías y tipografías usadas.

13. Lectura en voz alta en libros de texto, con las voces de Android (Readium TTS): reproducir, pausar, velocidad ajustable, y resaltar la frase que se está leyendo. Usar la voz del idioma del libro si está disponible en el móvil.

14. Estadísticas completas: tiempo leído por día, racha de días leyendo, libros y cómics terminados al año y velocidad media de lectura en libros.

Orden de construcción:
1. Proyecto base en Android Studio, con los textos en strings.xml desde el principio, y lector de EPUB y PDF con un libro de prueba, con el tema crema.
2. Biblioteca con estantes, biblioteca vacía, importar libros, carpeta de la app, editar sus datos, estados, estanterías, menú al mantener pulsado y guardar la posición.
3. Prueba temprana de la copia automática: elegir un archivo en Google Drive con el selector de Android, guardar el permiso y sobrescribirlo varias veces, para comprobar que funciona con Drive antes de seguir.
4. Opciones del lector de libros: barras, menú inferior plegable, panel de ajustes, índice, búsqueda, botones de volumen, pantalla encendida, brillo, tipografías y temas.
5. Lector de cómics y manga: CBZ, CBR y PDF de cómic; presentación tipo revista en la estantería; modos página, doble y scroll; sentido de lectura; zoom; recorte de márgenes; marcadores.
6. Número de página real o estimado y tiempo restante.
7. Subrayados, páginas destacadas, menú de selección, diccionario y traducción.
8. Tarjetas por libro, tarjeta abierta, modo Tarjetas de la biblioteca, etiquetas y búsqueda doble.
9. Tarjeta del día en la biblioteca y widgets de Android.
10. Pantalla Exportar con selección, guardar y compartir; importar archivos .libertalia; copia automática completa con el aviso de archivos pesados; pantalla Ajustes.
11. Lectura en voz alta.
12. Estadísticas.
13. Traducciones completas a los seis idiomas, selector de idioma y revisión de que todos los textos caben en todas las pantallas.
14. Icono de la app a partir de bocetos/icono-libertalia.svg: icono adaptativo de Android (fondo crema y la brújula en primer plano dentro de la zona segura), más una versión monocroma para los iconos temáticos de Android 13. Después, generar el APK firmado para instalarlo en mi móvil.

Para más adelante (no construir todavía, pero tenlo en cuenta al diseñar los datos y el lector):
- Libros escaneados (OCR): si un PDF o EPUB de texto tiene páginas que son solo imágenes, ofrecer "Reconocer texto" usando ML Kit Text Recognition en el propio móvil, sin servidores y sin coste. Procesar en segundo plano con barra de progreso, con opción de hacerlo solo mientras el móvil carga o empezando por las páginas que se van leyendo. Guardar el texto reconocido y la posición de cada palabra, y colocar una capa de texto invisible sobre la imagen para poder seleccionar, subrayar, buscar y crear tarjetas igual que en un libro con texto. Avisar de que puede contener errores; el texto de las tarjetas es editable para corregirlos.
- Compartir tarjetas en redes sociales: desde cada tarjeta, generar una imagen con la cita, el título y el autor del libro, y compartirla con el menú de compartir de Android (WhatsApp, Instagram, X, etc.). El usuario elige el fondo: varios diseños de la app (con el estilo crema por defecto), la portada del libro difuminada o una foto propia de su galería o cámara. Sobre fotos, añadir una capa semitransparente para que el texto se lea bien, y permitir elegir el formato (cuadrado, vertical para historias). También compartir solo el texto de la cita. La nota personal nunca se incluye salvo que el usuario lo elija.
- Más formatos: TXT, CB7 y carpetas de imágenes como cómic.

Trabajaremos una fase cada vez. Cuando una funcione, haz un commit de Git con un mensaje en español.
