# Libertalia Reader — lector de libros, cómics y manga para Android

## Cómo trabajar conmigo

- **Soy principiante en programación.** Explícame cada paso con palabras sencillas y dime cómo probarlo en el emulador o en mi móvil.
- **Una fase cada vez.** Antes de empezar una fase, resume qué vas a hacer; no pases a la siguiente hasta que confirme que funciona.
- **Commit de Git** cuando una fase funcione, con un mensaje en español.
- Si algo de este documento es ambiguo o contradictorio, **pregúntame** en vez de suponer.
- Comentarios del código y mensajes de commit en español. Textos de la interfaz siempre en `strings.xml` (nunca en el código).
- Avísame si alguna dependencia o tipografía tiene una licencia que impida incluirla en la app.

## Decisiones tomadas

- Nombre de paquete: **`org.libertalia.reader`** (definitivo, no cambiarlo).
- **Repositorio público en GitHub (decisión del usuario, 24/11):** `libertalia-reader`, licencia Apache 2.0 (`LICENSE`; la GPL no encaja con la licencia UnRAR de junrar). Se publicó con una historia nueva (un commit inicial) firmada con el correo anónimo de GitHub; la historia anterior se queda solo en local, en la rama `historia-privada`, que **nunca se sube**. Antes de cada subida: nada personal (títulos de libros del usuario, rutas del ordenador, correos, cuentas), ni claves (`keystore.properties`, `.jks`), ni libros o volcados del usuario. Subir (`git push`) solo cuando el usuario lo pida.
- Plan por fases aprobado: fase 0 (preparar el ordenador) y después las 14 fases del orden de construcción.

## Estado del proyecto

- Fase 0 (preparar el ordenador): hecha.
- Fase 1 (proyecto base y lector EPUB/PDF con libros de prueba y tema crema): hecha y probada en el emulador.
- Fase 2 dividida en tres pasos:
  - 2a (Room, elegir carpeta, importar libros, biblioteca vacía, lista provisional): hecho y probado.
  - 2b (estantes de madera, filtros por estado, «Seguir leyendo», guardar y recuperar la posición, Navigation Compose): hecho y probado. Base de datos en versión 2 (migración automática 1→2).
  - 2c (menú al mantener pulsado: editar datos, estado, estanterías, eliminar; «Abrir con»; organizar estanterías; icono de estado; orden leyendo → pendientes → terminados): hecho y probado. Base de datos en versión 3.
- Fase 2 terminada.
- Fase 3 (prueba de copia en Google Drive): hecha y probada en el emulador con Drive. Funciona: se elige `copia.libertalia` con ACTION_CREATE_DOCUMENT, el permiso permanente sobrevive a cerrar la app y se sobrescribe siempre el mismo archivo, sin duplicados. Pantalla provisional `PantallaPruebaCopia` (icono de nube en la biblioteca) y clase `copias/PruebaCopia`: en la fase 10 pasan a Ajustes y a la copia real con WorkManager.
  - Lección: con Drive no sirve comprobar el tamaño justo después de escribir (lo actualiza unos segundos más tarde). Para verificar, se vuelve a leer el archivo y se compara su huella.
  - Al sobrescribir se usa el modo "wt" y, si la nube no lo admite, "w".
- Fase 4 dividida en dos pasos:
  - 4a (barras, barra inferior plegable, índice, búsqueda, barra de progreso): hecho y probado.
  - 4b (panel Aa: temas, letra, tipografías, márgenes, interlineado, brillo, volumen, pantalla encendida, filtro PDF; páginas por pantalla; ajustes de texto por libro; marcapáginas): hecho y probado. Base de datos en versión 4.
  - 4c (modo texto para PDF): hecho y probado (con 19 PDF del usuario: notas al pie, cabeceras, escaneos). Código en `lector/modotexto/`:
    - `ExtractorPdf` (PdfBox: líneas con tamaño y posición; superíndices marcados; vuelve a juntar los trozos que PdfBox separa cuando una llamada volada o el número de una nota sube un poco, como en los PDF de Word), `LimpiezaTexto` (cabeceras/pies repetidos y números de página fuera, títulos por letra grande, párrafos, guiones, notas al pie, arreglo de «í » partida y espacios antes de signos), `GeneradorEpub` (EPUB interno por capítulos del índice del PDF o cada 10 páginas, máx. 25 páginas por parte; ancla `pag-N` por página; notas en `div.notas` detrás del párrafo que las llama), `ModoTexto` (caché en `files/modo-texto/<id>/libro-vN.epub` + `mapa-vN.json`; subir `VERSION` si cambia la limpieza; recuerda el modo por libro en las preferencias "modo_texto").
    - En la base de datos se sigue guardando la página del PDF; la posición exacta del texto va aparte. Botón en la barra superior de los PDF para cambiar de modo (cierra y reabre el lector).
    - Licencias: PdfBox-Android (Apache 2.0) y BouncyCastle, que viene con ella (MIT). Incluirlas en «Acerca de» (fase 10).
    - Pendiente para más adelante: estilo de notas en EPUB normales (`epub:type="footnote"`, `aside`); Readium las trata a su manera.
    - PDF escaneados con OCR (probado con el artículo de Núñez Martínez): tamaño de cuerpo por página, cabeceras/pies comparados solo por sus letras y con tolerancia a erratas, notas sin número reconocidas por el hueco que las separa del texto (y se quitan restos como «'» o «^»), guion invisible U+00AD al final de línea.
    - Interruptor «Separar las notas al pie» en el panel Aa (solo en modo texto, **por libro**, preferencia "notas_<id>" en "modo_texto"; por defecto activado). Desactivado: `LimpiezaTexto.limpiar(detectarNotas = false)` deja todo como párrafos; se guarda otra caché `libro-vN-sin-notas.epub`. Al cambiarlo se reabre el texto en la misma página del PDF.
    - Banco de pruebas de notas (19 PDF del usuario, nov. 2026): `VolcarPdfActivity` (solo en debug; `adb shell am start -n org.libertalia.reader/.depuracion.VolcarPdfActivity`) vuelca las líneas de todos los PDF de la carpeta a `files/volcados/`; se copian al ordenador (no al repositorio: son libros del usuario) y `VOLCADOS=<carpeta> ./gradlew :app:testDebugUnitTest --tests '*InformeNotas*'` escribe `app/build/informe-notas.txt` (llamadas sin nota, notas sin llamada, sin número, escapadas). Formato de volcado en `VolcadoLineas`. Pistas que usa ahora la limpieza: cuerpo medido arriba de la página; notas = grupo final del mismo tamaño; nota nueva solo con número volado (o el siguiente en orden); si el libro marca las llamadas voladas, una nota con número normal necesita su llamada cerca; párrafo que empieza con número volado = nota (notas al final del capítulo); nota que ocupa toda una página; notas que siguen en la página siguiente; ligaduras partidas («Th e», «fi nanzas»); bloques de 10 páginas sin cortar párrafos.
    - **Escaneos con llamadas de número normal (un ensayo escaneado de los años 50, 24/11):** el OCR leyó como volados los números de apartado de los títulos («¹ — Precocidad»), y la limpieza creía que el libro usaba llamadas voladas y descartaba sus notas (8 de ~90). Ahora: solo se da por hecho que un libro usa llamadas voladas si hay ≥3 a media frase (no al principio de un bloque) y al menos la mitad de las notas numeradas tienen su llamada en el libro (`usaLlamadasVoladas`); si no, las llamadas normales («el Informe”. 1 Muy», «autores)3») se marcan voladas cuando la página tiene esa nota (`marcarLlamadasNormales`). Con números normales, una línea con sangría empieza nota nueva aunque el número no siga el orden (el OCR lee «8» por «3»); en escaneos (menos de 5 voladas a media línea en todo el libro) también una línea con sangría y mayúscula tras una línea corta acabada (nota sin número). No empiezan nota «6tro» ni «237). Afirmar» (`NO_ES_NUMERO`). Una nota sin número que empieza en mayúscula tras una nota acabada no se pega a la de la página anterior. Prueba en `NotasEscaneoTest`. `VolcarPdfActivity` admite `--es filtro <texto>` para volcar solo un PDF. Caché del modo texto en versión 25.
      - Revisión del texto entero (24/11): un grupo de letra pequeña al pie, separado del texto, es nota también si la página anterior acabó en nota, o (solo en escaneos) si contiene notas numeradas o empieza con sangría; nunca si son líneas de índice (`LINEA_DE_INDICE`). Un resto diminuto bajo las notas («l ^ r O T l M O») se aparta para medirlas y se pega a la última. El interlineado de `bloquesDe` se mide solo entre líneas del tamaño del texto (si hay ≥5), para que las líneas no salgan como párrafos sueltos. No son títulos las líneas cuya primera letra es minúscula (salvo la 2.ª línea de un título grande o «v. Apartado») ni las que empiezan por «(» a media frase. El número de apartado volado al principio de un título («¹ — Precocidad») queda normal.
      - Para revisar un libro entero: `VOLCADOS=<carpeta> ./gradlew :app:testDebugUnitTest --tests '*InformeNotasTest.textoLimpio'` escribe `app/build/texto-limpio/<libro>.txt` (títulos con #, notas con [NOTA], continuaciones con (sigue)). Al tocar la limpieza, comparar esos textos de los 21 PDF antes y después.
    - Depuración: en la versión de pruebas se guarda `files/modo-texto/<id>/lineas-vN.txt` con las líneas tal como salen del PDF (leer con `adb exec-out run-as org.libertalia.reader cat ...`).
    - Prueba: `python herramientas/crear_pdf_notas.py` crea `ensayo-notas.pdf` (cabeceras, notas, guiones).
- Fase 4 terminada.
- Fase 5 (cómics y manga) dividida en tres pasos:
  - 5a (añadir CBZ, CBR y PDF de cómic; ComicInfo.xml; portada; cómics de frente en el estante con etiqueta «CÓMIC · 2» / «MANGA · 1»; menú: sentido de lectura, «Tratar como cómic/libro» en PDF, número de tomo al editar; aviso RAR 5): hecho y probado. Código en `comic/` (`ArchivoComic`, `FichaComic`, `DatosComic`, `OrigenComic`).
    - junrar 7.5.5 (licencia UnRAR: gratuita, se puede publicar; no es libre: prohíbe usarla para crear un compresor RAR). El usuario la aceptó. Incluirla en «Acerca de» (fase 10). Solo RAR 4 y anteriores; RAR 5 se detecta por la cabecera y avisa.
    - Un PDF es de cómic si sus primeras 8 páginas casi no tienen texto (PdfBox). Se puede cambiar en el menú.
    - Mientras no exista el lector (5b), los CBZ/CBR muestran un aviso al tocarlos y los PDF de cómic se abren en el lector de PDF (sin botón de modo texto).
  - 5b (lector de cómics página a página): hecho y probado (también con cómics reales del usuario desde Drive).
    - `LectorComicActivity` + `PantallaComic` (Compose, HorizontalPager; `reverseLayout` en manga) + `LectorComicViewModel`. Barras del boceto 13 sobre fondo `fondo_comic`; toque en el centro las oculta (y las del sistema). Toques en los lados (28 %), deslizar y volumen pasan página siguiendo el sentido. Doble toque y pellizco amplían (1–5x); ampliada, deslizar mueve la imagen. Botón del sentido (guarda con `LibroDao.cambiarSentido`). Barra de progreso en espejo en manga.
    - `FuentePaginas`: lee las páginas del archivo original sin copiarlo. CBZ: lector de ZIP propio (índice central, ZIP64, stored/deflate) sobre `FileChannel`. CBR: junrar con un `VolumeManager` propio sobre el mismo canal. PDF de cómic: `PdfRenderer` de Android. Si el archivo no permite saltar (nubes que lo dan como «tubo»), copia temporal en `cache/lectura/` que se borra al cerrar.
    - Caché LRU de páginas (1/5 de la memoria), imágenes en RGB_565 a 1,5x la pantalla.
    - Posición: `posicion = {"pagina": N}` (desde 1) con `guardarPosicion`; al abrir, `marcarAbierto` (pendiente → leyendo). Los PDF de cómic abiertos antes con Readium recuperan su `locations.position`.
    - Nunca leer archivos de cómic en el hilo de la pantalla: en Drive, abrir el archivo lo descarga entero (un cómic de 52 MB: 17 s la primera vez, 0,8 s después) y Android mostraba «no responde». `MainActivity` mira el permiso permanente sin abrir el archivo; el lector muestra «Abriendo… la primera vez puede tardar» con botón Volver.
    - RAR **sólidos** (todas las páginas en un bloque): saltar a una página da datos rotos (página negra). Se descomprimen en orden según se avanza y se guardan en `cache/lectura/<id>-paginas/` mientras se lee (se borra al cerrar).
    - Páginas negras al ampliar (doble toque): las páginas grandes se decodificaban hasta el doble de lo pedido y pasaban de ~4096 px, que la tarjeta gráfica no dibuja. Ahora se ajustan al tamaño pedido (1,5x pantalla, máximo 4096 px por lado).
    - **Página negra al ampliar (causa real, 23/11):** el límite de desplazamiento se calculaba con el tamaño de la pantalla, no con el del dibujo (que deja franjas oscuras arriba/abajo, enormes en páginas dobles). Al ampliar cerca del borde se veía el fondo. Ahora `limitar` usa el tamaño real de la página dibujada.
    - **Página «vacía» con doble toque real (23/11):** al levantar el dedo del segundo toque, el detector de doble toque activa el zoom y ese mismo evento llega al detector de pellizco/arrastre ya sin dedos: `calculateCentroid` da un punto no válido (NaN) y la página se movía fuera de la vista (se veía el color de los márgenes). Ahora el detector ignora eventos sin dedos y `limitar` descarta NaN. La simulación con teclas no lo reproducía porque no envía toques.
    - Para probar el zoom desde adb (no sabe hacer doble toque y el emulador «con Google Play» no deja `adb root`/`sendevent`): en la versión de pruebas, en el lector de cómics, `adb shell input keyevent KEYCODE_Z` (centro), `KEYCODE_X` (arriba) y `KEYCODE_C` (abajo) ejecutan el mismo código que el doble toque.
    - Pendiente: probar un CBR sólido de verdad.
- Fase 5 terminada.
- Fase 6 (página del libro y «Seguir leyendo» sin porcentaje): hecha y probada. Base de datos en versión 5 (`paginaLector`, `paginasLector`).
  - **Decisiones del usuario: nada de tiempo restante ni de página en papel** (ni real «p. 123» ni estimada «≈ p. 123», ni ISBN ni Open Library): se probaron y se quitaron porque descompensaban la barra y no serían fiables. La app no usa internet. Las columnas `paginasImpresas` e `isbn` siguen en la base de datos sin usarse.
  - «Seguir leyendo»: «Capítulo · Pág. X de Y» (cómics: «Pág. X de Y») con las páginas del lector que se guardan en `guardarPosicion`. Los libros no abiertos desde la v5 no muestran página hasta abrirlos.
  - Barra de progreso: la última página lleva siempre al final real del libro (`finalDelLibro`), aunque el último capítulo aún no esté medido o el total cambie mientras se arrastra (antes el salto se quedaba corto o no ocurría).
- Fase 7 (subrayados, notas, páginas destacadas, diccionario): hecha y probada. Base de datos en versión 6 (tabla `tarjetas`, `datos/Tarjeta.kt` y `TarjetaDao`).
  - Decisiones del usuario: **sin «Traducir»**. Diccionario y Copiar funcionan sin subrayar antes. **La nota solo en los fragmentos subrayados** (se pone tocando el subrayado) y se puede borrar. Las páginas destacadas llevan una **descripción breve opcional** (80 letras) que hará de título en sus tarjetas.
  - Para la fase 8 (lo pidió el usuario): la tarjeta muestra su nota abajo, como una nota al pie, y lleva al punto exacto del libro (`Tarjeta.posicion`, Locator de Readium).
  - **Decisión del usuario (cambia la descripción original): «Tarjetas» y «Destacadas» van SIEMPRE separadas, nunca juntas.** «Tarjetas» = solo subrayados (botón «Tarjetas» en el lector, modo Tarjetas de la biblioteca, menú del libro). «Destacadas» = solo páginas destacadas, con su propia lista y su propio botón. En la base de datos siguen en la misma tabla (`tipo`), pero ninguna pantalla las mezcla. Por eso el filtro «por tipo» de la descripción original ya no hace falta.
  - ~~Decisión del usuario (fase 9): la tarjeta del día sale solo de los subrayados~~ (anulada: ver la decisión de la fase 9).
  - Menú al seleccionar (EPUB y modo texto): «● Amarillo/Verde/Azul/Rosa» (punto pintado), y en el ⋮: Diccionario, Copiar, Marcapáginas. En el PDF original no se puede seleccionar (Readium).
  - **Modo texto (lo pidió el usuario):** se puede subrayar y destacar. Los subrayados guardan el Locator del texto más `"libertalia": {"paginaPdf": N}` (`CLAVE_PROPIA`) y Readium los vuelve a encontrar por su propio texto (antes/después), así que sobreviven a cambios de la limpieza mientras no cambie el archivo del capítulo. Las páginas destacadas en modo texto se guardan como **posición del PDF** (`MapaTexto.posicionPdf`): la misma destacada se ve en el PDF original y en el texto; para ir a ella desde el texto se usa `MapaTexto.posicionTexto`.
  - Tocar un subrayado (`DecorableNavigator.Listener`, grupo "subrayados") abre `HojaSubrayado`: colores, añadir/editar/borrar nota, quitar subrayado. Archivo `lector/Subrayados.kt` (también `FraseCompleta`, la frase de punto a punto con el texto de antes/después que da Readium).
  - Estrella en la barra superior (todos los libros de texto, no cómics): destaca la página que se ve y pide la descripción; si ya está destacada (roja), deja cambiarla o quitarla. En EPUB cuenta la destacada que empieza dentro de la pantalla visible; en PDF y modo texto, la página del PDF.
  - Botón «Destacadas» en la barra inferior (lo pidió el usuario): lista con la descripción como título y debajo el capítulo (y «Pág. N» en PDF); tocar lleva a la página, el lápiz cambia la descripción o la quita.
  - Botones de la barra inferior a partes iguales (`RowScope.BotonBarra`, peso 1); si la palabra no cabe, la letra se encoge (`BasicText` con `TextAutoSize`, mínimo 9 sp) en vez de partirla.
  - Diccionario: `ACTION_PROCESS_TEXT` con la lista de apps del sistema (se quitan los traductores); siempre elige el usuario, nunca se abre una sola app sin preguntar (en el emulador la única es Gemini). `<queries>` en el manifiesto.
  - Las tarjetas borradas se marcan `eliminada = 1` (para que una copia antigua no las resucite en la fase 10).
  - Aviso «Al subrayar se crea una tarjeta con la frase completa» 4 s (sin botón «Ver» hasta la fase 8).
- Fase 8 dividida en dos pasos:
  - 8a (tarjetas de un libro y tarjeta abierta): hecho y probado. Código en `tarjetas/`:
    - `TarjetasActivity` (pantalla propia con NavHost: lista y `tarjeta/{id}`), `TarjetasViewModel`, `PantallaTarjetas` (boceto 4: cabecera con portada y «N tarjetas · en el orden del libro», filtros Todas + 4 colores, cuadrícula escalonada de 2 columnas; en cada tarjeta punto de color, «Pág. N» solo en PDF, capítulo a la derecha, frase con el subrayado resaltado —recortada con «…» si hay mucho texto antes—, nota al pie tras línea discontinua y etiquetas) y `PantallaTarjeta` (boceto 8: «Tarjeta X de N», compartir —cita, título y autor, nunca la nota—, borrar con confirmación, libro/autor/capítulo sin porcentaje, colores, «Editar texto» con dos campos —texto subrayado y frase—, «Mi nota» que se guarda sola, etiquetas con «+ Añadir» y ×, fechas, flechas e «Ir al libro»).
    - Solo subrayados (`TarjetaDao.observarSubrayados`); el título y el autor se leen del libro (`LibroDao.observar`).
    - Entradas: botón «Tarjetas» en la barra inferior del lector, «Ver» en el aviso al subrayar y «Ver tarjetas» en el menú del libro (solo libros de texto). Contrato `TarjetasActivity.Abrir`: «Ir al libro» devuelve la tarjeta; el lector va a ella (`destinoDeTarjeta`: en el PDF original, a su página) y la biblioteca abre el libro en ella (`MainActivity.abrirLibro(libro, destino)`; un subrayado de un PDF abre el modo texto).
    - Orden: `progreso`; como Readium a veces no da la posición exacta de la selección, se suma la altura en pantalla × 1e-5 para ordenar las de una misma pantalla.
    - Las fechas usan el formato del idioma del móvil (en el emulador, que está en inglés, sale «November 21»).
  - 8b (modo Tarjetas de la biblioteca, filtro por etiqueta, búsqueda en un libro y en todos, etiquetas de todos los libros): hecho y probado.
    - Modo Tarjetas (boceto 7): botón oscuro «Tarjetas»/«Libros» bajo «Seguir leyendo» (`BotonModo` en `Estanterias.kt`), título «Tarjetas», aviso «Toca un libro para ver sus tarjetas», círculo con el número de tarjetas en cada libro (`TarjetaDao.observarConteo`) y cómics apagados y sin tocar. El modo vive en `BibliotecaViewModel.modoTarjetas`, así que sigue al volver de las tarjetas de un libro.
    - Lupa en la cabecera de la biblioteca: `PantallaBuscarTarjetas` (ruta "buscar-tarjetas"), busca en texto, frase, nota y etiquetas de todos los libros (desde 2 letras, sin distinguir mayúsculas ni tildes: `BuscarTexto`), resultados agrupados por libro; tocar uno abre esa tarjeta.
    - En las tarjetas de un libro: lupa para buscar dentro y botón «Etiquetas» (solo si hay) con la lista de etiquetas del libro; la elegida se ve como «etiqueta ×».
    - **Etiquetas (petición del usuario):** tocar una etiqueta (en una tarjeta pequeña, o el nombre en la tarjeta abierta; su × la quita) lleva a `PantallaEtiqueta`: todas las tarjetas de todos los libros con esa etiqueta, cada una con el título de su libro arriba (`TarjetaPequena(conTitulo = true)`). Ruta "etiqueta/{nombre}" en `TarjetasActivity` y en `MainActivity`.
    - **Buscador de etiquetas** en el modo Tarjetas de la biblioteca (`BuscadorEtiquetas`): campo y debajo las etiquetas que coinciden con su número de tarjetas (como mucho 3 filas, que se deslizan juntas hacia los lados si hay más; 1 fila hasta 4 etiquetas, 2 hasta 9). **Decisión del usuario: las etiquetas van de la más usada a la menos** (empates por orden alfabético, `TodasLasEtiquetas`), y se leen por filas: la primera fila tiene las más usadas. La lista de etiquetas de cada libro también va por número de usos. Tocar una abre su pantalla.
    - **Pendiente (el usuario):** puede que más adelante cambie la forma de ver las etiquetas del buscador; las 3 filas deslizables no le convencen del todo.
    - Una tarjeta de otro libro (desde una etiqueta) se abre en otra `TarjetasActivity`; su «Ir al libro» se pasa hacia atrás. Si quien abrió las tarjetas era el lector de otro libro, este pide a `MainActivity.abrirTarjetaEnLibro` que abra el libro nuevo y se cierra (cerrando solo su propia publicación).
- Fase 8 terminada.
- Fase 9 (racha de lectura y widgets): hecha y probada.
- **Decisión del usuario (fase 9, cambia la descripción original): fuera la tarjeta del día del todo** (ni bloque en la biblioteca, ni interruptor en el menú, ni widget; la columna `incluirEnTarjetaDelDia` queda sin usar). En su lugar, una **racha de lectura** al estilo de Duolingo (diseño propio):
  - Personaje: **un pirata con brújula** (dibujo propio, estilo crema y marrón de la app).
  - Un día cuenta como leído con **al menos 5 minutos** leyendo un libro o cómic (solo tiempo activo).
  - **4 estados:** hoy ya has leído (contento) · aún no has leído hoy (tranquilo) · desde las 20:00 sin haber leído, racha en peligro (asustado) · racha rota (triste, empieza de cero).
  - Widgets: el de la racha y el de «Seguir leyendo» (sin porcentaje ni minutos).
  - La racha se ve **solo en el widget** (no dentro de la app) y **sin notificación**.
  - Hecho (fase 9): tabla `dias_lectura` (fecha, segundos; `datos/DiaLectura.kt`), base de datos en versión 7.
  - `racha/Racha.kt`: `CalculoRacha` (5 min = 300 s, peligro desde las 20:00; pruebas en `CalculoRachaTest`) y `MedidorLectura`: suma el tiempo entre pasos de página (cada hueco cuenta 3 min como mucho), guarda cada 30 s y al salir del lector, y luego redibuja los widgets. Lo usan `LectorActivity` y `LectorComicActivity` (onResume/onPause).
  - `widgets/Widgets.kt` (Jetpack Glance 1.2.0, Apache 2.0; incluir en «Acerca de»): `WidgetRacha` (pirata que crece con el tamaño del widget, apaisado si es ancho) y `WidgetSeguirLeyendo` (fondo marrón, título, capítulo y «Pág. X de Y» en líneas separadas, barra; al tocarlo abre el libro con `MainActivity.abrirLibroIntent`). Se actualizan cada 30 min y al leer.
  - Pirata animado (petición del usuario): de cuerpo entero, con tricornio, parche, casaca, fajín rojo, pata de palo, un libro en una mano y la brújula en la otra. 4 fotogramas por estado (`pirata_<estado>_<1-4>`, vectores propios) que genera `python herramientas/crear_pirata.py` (también `herramientas/pirata-vista-previa.html` para verlos animados). En el widget, `widget_pirata_animado.xml` (ViewFlipper cada 550 ms) metido con `AndroidRemoteViews`: contento da saltitos y guiña, tranquilo respira y parpadea, asustado tiembla y suda, triste agacha la cabeza y llora. Un widget no admite animaciones más fluidas.
  - Pendiente: la vista previa de «Seguir leyendo» en la lista de widgets del móvil sale vacía (solo el icono).
- Fase 10 dividida en cuatro pasos: 10a (formato .libertalia y pantalla Exportar), 10b (importar copias), 10c (copia automática con WorkManager y aviso de archivos pesados; fuera la prueba de la fase 3), 10d (pantalla Ajustes, «Acerca de» con licencias y exportar las tarjetas de un libro a Markdown, CSV y Anki).
  - Decisiones del usuario (fase 10):
    - **Ajustes sin racha:** las estadísticas rápidas son solo libros terminados este año y tiempo leído esta semana. La racha sigue solo en el widget.
    - **Archivos pesados:** como en la descripción: más de 50 MB (configurable) → preguntar «Incluir en la copia» o «Solo la página y los marcadores», con «Recordar mi elección». Vale también para los cómics, aunque no estén en la carpeta.
    - **Anki:** archivo de texto que Anki importa (anverso: frase con el subrayado; reverso: libro, capítulo y nota), sin .apkg.
    - La copia guarda también los días leídos (racha) para no perderla al cambiar de móvil.
    - **Aviso si falla la copia automática (decisión del usuario, 23/11):** notificación solo cuando falla (nunca cuando va bien) y además aviso dentro de la app (biblioteca y Ajustes).
    - **Espacio y duplicados (decisiones del usuario, 23/11):**
      - **Mover libros:** al añadir un EPUB/PDF que está fuera de la carpeta Libertalia (p. ej. Descargas), se copia a la carpeta y se pregunta «¿Borrar el original?» con «Recordar mi elección». Solo si el original está en el móvil (nunca en una nube) y Android deja borrarlo; si no, no se ofrece. Los cómics no cambian (no se copian).
      - **Borrar tras importar:** la copia temporal de la app se borra sola. Al terminar de importar se ofrece borrar el archivo .libertalia solo si está en el móvil (p. ej. llegó por WhatsApp a Descargas); nunca si está en una nube ni si es el archivo de la copia automática (el de la nube es el que se sigue sobrescribiendo).
      - **Liberar espacio (paso 10d, en Ajustes):** revisar la carpeta Libertalia: archivos repetidos (mismo contenido, p. ej. «Tomo 10.cbr (1)») y archivos que no son de ningún libro; enseñar cuánto ocupan y borrarlos tras confirmar.
      - Límite de Android: la app solo puede borrar archivos que el usuario le ha dado (selector) o que ha creado ella; no puede buscar copias por todo el móvil ni en Drive.
  - 10d (Ajustes, Acerca de, Liberar espacio, exportar tarjetas): hecho y probado (confirmado por el usuario). Código en `ui/ajustes/`:
    - `PantallaAjustes` + `AjustesViewModel`: estadísticas (libros terminados este año, tiempo leído esta semana; sin racha), bloque de copias, bloque «Ajustes» (idioma, tipografía, carpeta —solo se muestra, no se cambia—, límite de archivos pesados 25/50/100/200/500 MB, qué hacer con el original al añadir y con los archivos pesados: borrar/conservar, incluir/solo datos o «Preguntar cada vez»), bloque «Lector de cómics» (modo por defecto: automático, página, doble o scroll —preferencias "comic", clave "modo_defecto"— y recortar márgenes) y «Acerca de». `DialogoOpciones` genérico.
    - `AjustesApp`: tipografía de toda la app (`familiaDe` en `TemaLibertalia`, se cambia al momento), idioma y ajustes por defecto de los cómics.
    - **Idioma:** selector con `AppCompatDelegate.setApplicationLocales` + `locales_config.xml` + servicio `AppLocalesMetadataHolderService` (autoStoreLocales). Android lo guarda (`cmd locale get-app-locales`), pero **las traducciones llegan en la fase 13**: hasta entonces todo sigue en castellano. Los nombres de los idiomas van en su propia lengua (Galego, Euskara...), sin traducir.
    - `LiberarEspacio`: revisa la carpeta Libertalia; los archivos de libros de la biblioteca nunca se tocan. Repetidos (misma huella que un libro u otro archivo suelto) vienen marcados; los que no son de ningún libro, sin marcar. Borra con `DocumentsContract.deleteDocument` tras confirmar. Probado en el emulador con un archivo de prueba.
    - `PantallaAcercaDe`: icono (`drawable/icono_libertalia.xml`), versión, filosofía y licencias (Readium, PdfiumAndroid, PDFium, PdfBox-Android, Bouncy Castle, junrar con nota UnRAR, Jetpack, Kotlin, Literata, Lora, Atkinson Hyperlegible, OpenDyslexic).
    - `tarjetas/ExportarTarjetas`: botón en la cabecera de las tarjetas de un libro → Markdown, CSV (con marca UTF-8 para las tildes) o Anki (texto con `#separator:tab`, `#html:true`, `#tags column:3`; anverso: frase con el subrayado en negrita; reverso: libro, autor, capítulo, «Pág. N» solo en PDF y nota; etiquetas como etiquetas de Anki). Se escribe en `cache/compartir/` y se abre el menú de compartir. Subrayado y frase se comparan sin saltos de línea.
    - Tema: `surfaceContainer*` en crema (los menús desplegables salían lilas).
  - 10c (copia automática): hecho y probado. Código en `copias/`:
    - `CopiaAutomatica` (en `LibertaliaApp.copiaAutomatica`; preferencias "copia_automatica": uri, dias, fecha_ultima, resultado, huella_datos). Un único archivo elegido con CreateDocument (permiso permanente) que se sobrescribe. `hacer(forzar)`: si no es forzada y la «huella de los datos» (libros con sus fechas, tarjetas, estanterías, días leídos y preferencias por libro) no ha cambiado desde la última correcta → `SIN_CAMBIOS`, no toca el archivo. Prepara la copia en `cache/copia-automatica/`, sustituye el archivo y vuelve a leerlo para comparar la huella. Al elegir el sitio por primera vez hace ya una copia. Hereda el archivo de la prueba de la fase 3 si existía.
    - `TrabajoCopia` (WorkManager 2.10.1, Apache 2.0; incluir en «Acerca de»): periódico cada N días (1, 2, 3, 7, 15, 30 o nunca; 3 por defecto) con batería no baja; nunca mientras se lee (`MedidorLectura.leyendo` → reintenta). «Hacer copia ahora» = trabajo único forzado con progreso. `LibertaliaApp` lo programa al arrancar (KEEP).
    - Fallo (`SIN_PERMISO`, `NO_EXISTE`, o `ERROR` tras 2 reintentos): notificación (`AvisoCopia`, canal "copias", solo si falla; permiso POST_NOTIFICATIONS que se pide al elegir el sitio) que abre la pantalla de la copia (`MainActivity.EXTRA_ABRIR_COPIA_AUTOMATICA`); además, aviso en la biblioteca al abrir la app y recuadro rojo en la pantalla y en Ajustes.
    - `ui/copias/PantallaCopiaAutomatica` (`RutaCopiaAutomatica`, ruta "copia-automatica"): dónde se guarda, cada cuánto, peso aproximado, última copia correcta y «Hacer copia ahora». Fila «Copia automática» en Ajustes con resumen. Fuera `PruebaCopia` y `PantallaPruebaCopia`.
    - Archivos pesados (boceto 14, `DialogoArchivoPesado`): al añadir (ANADIDO) un archivo de más de `Preferencias.limitePesadoMb` (50 por defecto; en 10d se podrá cambiar) se pregunta «Incluir en la copia» / «Solo la página y los marcadores» con «Recordar» (`Preferencias.incluirPesados`). Interruptor «Incluir el archivo en la copia automática» en el menú del libro (`LibroDao.cambiarIncluirEnCopia`).
    - Probado en el emulador (23/11): primera copia al elegir Descargas (427 MB); con el reloj adelantado 3 días la copia programada se ejecutó y no rehízo nada (sin cambios); «Hacer copia ahora» sobrescribe el mismo archivo con progreso; al borrar el archivo desde la app Archivos: recuadro rojo, notificación y aviso en la biblioteca; aviso de archivo pesado e interruptor del menú. Si solo se borra el archivo con `rm` (sin quitarlo de Descargas), Android lo vuelve a crear al escribir: la copia se arregla sola.
    - Para probar la copia programada: `adb shell settings put global auto_time 0` y `adb shell cmd alarm set-time <ms>` (adelantar 3 días); WorkManager la lanza sola. Después, `settings put global auto_time 1`. `cmd jobscheduler run -f -n androidx.work.systemjobscheduler org.libertalia.reader <id>` no sirve antes de tiempo (WorkManager la retrasa).
  - **Etiquetas desde el texto (petición del usuario, 23/11):** al tocar un subrayado en el lector, la hoja muestra sus etiquetas («#etiqueta ×» para quitarla) y «Añadir etiqueta». El diálogo «Nueva etiqueta» (`tarjetas/DialogoEtiqueta`, compartido con la tarjeta abierta) lleva el «#» fijo delante (no se puede borrar; si se escribe otro, se quita) y sugiere etiquetas que ya existen mientras se escribe (`ReglasEtiquetas.sugerencias`: primero las que empiezan igual —«fil» → #filosofía—, luego las que lo contienen, de la más usada a la menos, sin tildes ni mayúsculas; sin escribir nada, las más usadas). Tocar una sugerencia la pone. Pruebas en `ReglasEtiquetasTest`.
  - 10b (importar copias): hecho y probado. Código: `copias/ImportadorCopias` (leer → copia temporal en `cache/importar/`; `analizar` sin tocar nada; `aplicar` con las reglas de unión: libros por huella, datos del libro por `fechaModificacion`, posición por `fechaUltimaLectura`, tarjetas por id y fecha, estanterías por id o nombre, días con el máximo `DiaLecturaDao.guardarMaximo`, preferencias por libro solo si no existen; los archivos van a la carpeta Libertalia, también los de cómics), `ImportarViewModel`, `ui/copias/PantallaImportar` + `RutaImportar` (ruta "importar?uri="; la navegación ya descodifica el argumento).
    - Entradas: Ajustes → «Importar una copia», botón en la biblioteca vacía, y el importador de libros reconoce un ZIP con `manifiesto.json` (`ResultadoImportacion.COPIA`) al llegar por + o «Abrir con» (filtros application/zip, x-zip-compressed y octet-stream).
    - «Mover»: `Importacion.originalBorrable` + `ArchivosExternos` (solo documentos del móvil, fuera de la carpeta, con FLAG_SUPPORTS_DELETE) + `DialogoBorrarOriginales`; la elección recordada en `Preferencias.borrarOriginales` (en 10d, poder cambiarla en Ajustes). Tras importar, `EstadoImportar.Hecho.copiaBorrable` ofrece borrar el .libertalia (nunca el de la copia automática).
    - Probado en el emulador (23/11): exportar todo → `pm clear` → elegir carpeta → importar → base de datos idéntica (30 libros, 23 tarjetas, estantería, 7 días, portadas y preferencias por libro). No va en la copia: los ajustes generales (p. ej. «recortar» de los cómics). Los 3 cómics de prueba sin archivo quedaron «sin archivo».
    - Emulador: tras `pm clear` el selector decía «No se puede usar esta carpeta» en todas (proveedor de almacenamiento con `NoSuchFileException /storage/emulated`); se arregló reiniciando el emulador (`adb reboot`).
    - ¡Cuidado al probar! El selector de «Guardar en…» recuerda el último sitio: en el emulador puede abrir una nube con una cuenta real: comprobar siempre el destino.
  - 10a (formato .libertalia y pantalla Exportar): hecho y probado. Código en `copias/` y `ui/copias/`:
    - `FormatoCopia`: estructura del ZIP y JSON de libro, tarjeta, estantería y día leído (de ida y de vuelta; la vuelta es para 10b). `VERSION = 1`. Carpeta de cada libro: `libros/<titulo-sin-tildes>-<8 letras del id>/` con `libro.<formato>`, `portada.jpg` y `datos.json` (libro, ids de sus estanterías, todas sus tarjetas —también las borradas— y sus preferencias por libro de los archivos "modo_texto" y "comic", claves `*_<id>`). `manifiesto.json`: versión, fecha, lista de libros, todas las estanterías y todos los días leídos.
    - `MarkdownTarjetas`: `legible/<Título>.md` con secciones «Tarjetas» y «Destacadas» separadas; «Pág. N» solo en PDF, como en la app. Textos en strings.xml (`md_*`).
    - `Exportador`: escribe la copia en un archivo de la caché (`cache/compartir/`), con los libros sin comprimir (ya lo están) y avance de 0 a 1. Si un archivo no se puede leer, la copia sigue sin él y al final se avisa con un cuadro que nombra esos títulos. `Exportador.escribirEn` copia encima del archivo elegido («wt» y, si no, «w»).
    - `ExportarViewModel` + `PantallaExportar` (boceto 11) + `RutaExportar` (selector «Guardar en…» con CreateDocument y menú de compartir con FileProvider `${applicationId}.archivos`, solo `cache/compartir/`). Aviso de correo a partir de 25 MB. Nombre: `libertalia-AAAA-MM-DD.libertalia` o el título si es un solo libro.
    - Entradas: «Exportar» en el menú del libro (solo ese marcado) y Ajustes → Exportar (todos). El engranaje de la biblioteca abre ya una primera `PantallaAjustes` (aviso de privacidad y bloque «Copias y cambio de móvil» con Exportar y la prueba de copia de la fase 3).
    - Probado en el emulador: Quijote solo (10 kB, Markdown correcto), todo con archivos (427 MB en ~10 s, ZIP sano), compartir abre el menú de Android.
- Fase 10 terminada.
- Fase 11 (lectura en voz alta): **en pausa hasta terminar toda la app (el usuario, 24/11: «ya arreglaremos las voces cuando se finalice la app entera»)**. Sigue sin commit. Lo que ya funciona puede quedarse en la app mientras tanto.
  - Hecho: `voz/LecturaEnVoz` (Readium TTS `TtsNavigatorFactory`, solo voces locales —`requiresNetwork = false`—, elegidas por idioma y país; velocidad 0,75–2× en preferencias "voz"), `voz/ServicioVoz` (MediaSessionService de media3 1.11.0, notificación con controles; permisos FOREGROUND_SERVICE y _MEDIA_PLAYBACK), `voz/PanelVoz` (panel y avisos), `voz/SinNotas` (filtro del servicio de contenido que se salta `div.notas`, puesto en `ServicioLectura` con `onCreatePublication`), botón «Escuchar» en la barra inferior, resaltado de la frase (color `voz_frase`) y paso de página, PDF → modo texto con `EXTRA_ESCUCHAR`, `MedidorLectura` con contador de medidores activos.
  - Probado en el emulador (EPUB del Quijote): lee con voz local (es-us-x-esc-seanet-embedded), resalta, pasa página sola, frase siguiente, velocidad 1,25×, sigue con la pantalla apagada y con notificación; al salir del lector se para.
  - Hecho y probado el 24/11: «Leer en castellano» para libros que dicen mal su idioma (recordado por libro, clave "idioma_<id>"; el nombre del idioma sale en el de la app y el castellano como «castellano», `nombre_castellano`); el libro deja sitio al panel mientras se escucha (`ALTO_PANEL_VOZ_DP`, se recoloca al empezar y al parar); resaltado más visible (`voz_frase` #B97A3A); velocidad con el formato del idioma de la app («1,25×»); Media3 (Apache 2.0) en «Acerca de»; PDF → modo texto y empieza a leer solo.
  - **Pendiente:** (1) **no se saltan las notas al pie:** en Readium 3.4 `PublicationOpener` guarda `onCreatePublication` pero nunca lo llama (comprobado con javap y con mensajes de depuración), así que `FabricaSinNotas` no se activa. Otra vía: crear una `Publication` nueva para la voz con un `ContentService` propio, o envolver `publication.content()`. (2) Comprobar que el tiempo escuchando suma en la racha (el 24/11 la semana marcaba 10 min tras escuchar el 23/11; parece que sí, falta confirmarlo). (3) Una vez se abrieron solos los ajustes de voz tras «Falta la voz» (no se pudo repetir). (4) Voces más naturales (opciones abajo).
  - **Voces más naturales (lo pidió el usuario, 23/11; decidir mañana):** (a) en el móvil real las voces de Google sin conexión suenan mejor que en el emulador, y hay varias por idioma para descargar (Ajustes → Texto a voz → Google → Instalar datos de voz); la app ya elige la de más calidad. (b) Elegir la voz dentro de la app, con prueba («Escuchar ejemplo»), entre las del móvil (Readium lo permite con `AndroidTtsPreferences.voices`). (c) Si el usuario tiene otro motor de voz (p. ej. Samsung), se elige en Android como motor preferido. (d) Voces neuronales dentro de la app, sin internet (Piper / sherpa-onnx, Apache 2.0/MIT): mucho más naturales, pero +60–100 MB por voz, más trabajo (motor propio para Readium) y **cada modelo de voz tiene su licencia** (algunas no permiten distribuirlas): revisar una a una. Las voces de internet de Google descartadas: mandarían el texto a un servidor.
- Fase 12 (estadísticas): hecha y probada (confirmada por el usuario). Decisiones del usuario (24/11): **la racha sigue solo en el widget** (no en Estadísticas); **velocidad en palabras por minuto** (solo libros, no depende de la letra).
  - Base de datos v8 (`AutoMigration(7, 8, spec = Migracion7a8)`; probada instalando encima con los datos del emulador): `Libro.fechaTerminado` (la pone `LibroDao.cambiarEstado` al pasar a terminado y la quita al salir; los ya terminados toman `fechaUltimaLectura` o `fechaModificacion`) y en `dias_lectura` las columnas `segundosLibros`, `segundosComics`, `palabras` y `segundosPalabras`. La copia (.libertalia) y el importador las llevan (de cada día, lo mayor).
  - `MedidorLectura(app, comic)`: separa libros y cómics; `actividad(palabras)` recibe las palabras de la página recién leída cuando se pasa justo a la siguiente (`LectorViewModel.palabrasDePagina`: palabras del capítulo, contadas con `\p{L}+`, entre sus páginas). Para la velocidad no cuentan los huecos de 3 min o más ni las páginas a más de 1500 palabras/min; lo escuchado no cuenta para la velocidad (sí para el tiempo). El PDF original no mide palabras (el modo texto sí).
  - `ui/ajustes/Estadisticas.kt` (`EstadisticasViewModel` + `PantallaEstadisticas`, ruta "estadisticas"): gráfico de barras propio (Canvas) por semana (7 días), mes (30) o año (12 meses), total, media diaria (desde el primer día con datos) y «Libros · Cómics»; velocidad (a partir de 1 min medido); terminados por año con libros y cómics y los títulos al tocar. Entradas: fila «Estadísticas» en Ajustes y las dos cifras de arriba de Ajustes. `fechaTerminado(libro)` también la usa la cifra «libros terminados este año».
  - Probado: migración sin perder nada (26 libros, portadas, terminados), las tres vistas, títulos de 2026, «Libros: 2 min» tras leer y 157 palabras/min leyendo el ensayo a 45 s por página.
- Fase 13 (traducciones): hecha y probada (confirmada por el usuario).
  - `values-en`, `values-fr`, `values-ca`, `values-gl` y `values-eu` (509 textos cada uno, con plurales). **No se editan a mano:** se generan con `python herramientas/traducciones/generar.py` a partir de `herramientas/traducciones/<idioma>.tsv` («clave<TAB>texto»; plurales «clave#one»/«clave#other»). El programa avisa si falta o sobra una clave o si los datos (%1$s, %d…) no coinciden con el castellano, y escapa apóstrofos y comillas. Al añadir un texto nuevo en castellano, añadirlo también en los cinco .tsv y volver a generar.
  - Fechas y números con el idioma de la app, no el del móvil: `AjustesApp.localeApp`, `AjustesApp.fecha`, `AjustesApp.fechaHora` (usa `codigo_idioma` de cada idioma). `nombre_castellano` en cada idioma («castelán», «gaztelania»…).
  - Probado: emulador en inglés → la app sale en inglés sola; euskera, francés y gallego desde Ajustes (cambia al momento, sin cerrar el libro); todo cabe (lo largo pasa a dos líneas y la barra del lector encoge la letra, p. ej. «Nabarmenduak»). En el lector de cómics, «Scroll» se tradujo por una palabra corta («Continuo», «Continu», «Jarraitua») porque «Desprazamento» se partía.
  - Los títulos «Páginas 1–4» del modo texto se escriben dentro del EPUB generado: quedan en el idioma que hubiera al crearlo.
  - **Conviene que una persona nativa revise gallego, euskera y catalán** (sobre todo el euskera).
- Fase 14 (icono y APK firmado): icono hecho (ver «Icono» más abajo); APK firmado generado el 24/11 (falta que el usuario lo instale y lo confirme).
  - Clave: un archivo `.jks` **fuera del proyecto**, alias `libertalia`, PKCS12, RSA 4096, 100 años, con una contraseña aleatoria guardada solo en `keystore.properties` (raíz del proyecto, en .gitignore). **Nunca subir ni copiar a Git la clave ni ese archivo.** El usuario debe guardar las dos cosas en un sitio seguro.
  - `app/build.gradle.kts` lee `keystore.properties` (archivo, alias, contrasena) y firma la versión release; sin ese archivo, la release sale sin firmar. `./gradlew :app:assembleRelease` → `entregas/Libertalia-Reader.apk` (48 MB).
  - Firma distinta de la de pruebas: para instalarlo hay que desinstalar la versión de pruebas (antes, Exportar con archivos y luego Importar).
- **Arreglos pedidos por el usuario (24/11), en este orden:** (1) notas al pie de un PDF escaneado con llamadas de número normal (hecho y guardado); (2) «Abrir con…» (hecho, probado en el emulador; falta que lo confirme el usuario); (3) vinculación con Obsidian.
  - **«Abrir con…» / «Compartir» (24/11):** `BibliotecaViewModel.recibirDeOtraApp` → `Importador.examinar` (copia a temporal, huella, formato, ¿ya existe?). Si ya está en la biblioteca, se abre (y si es un libro de la carpeta se ofrece quitar el original). Si es nuevo: `DialogoAnadirOSoloLeer` → «Añadir» (`Importador.completar`) o «Solo leer» (`Importador.prepararDePaso`). El botón + sigue igual (`importar` = examinar + completar).
  - **Solo leer:** libro «de paso» (`biblioteca/LibroDePaso`, id `de-paso-<uuid>`, nunca en la base de datos; `Libro.esDePaso`). Su archivo es una copia en `cache/de-paso/`. Las órdenes de guardar posición sobre un id que no existe no hacen nada. Se ocultan subrayar, destacar, marcapáginas, Tarjetas, Destacadas y Escuchar (`LectorViewModel.soloLeer`), y en cómics los marcadores; el medidor de la racha no empieza. El modo texto funciona. Al cerrar el lector (`isFinishing`, no al cambiar de modo) `LibroDePaso.limpiar` borra la copia, `files/modo-texto/<id>` y las preferencias con ese id; `LibertaliaApp` borra restos al arrancar. El lector de cómics lo encuentra con `LibroDePaso.actual`.
  - **Quitar el original al añadir:** `DialogoMoverOriginal` (recuerda la elección en `Preferencias.borrarOriginales`, la misma de Ajustes). Solo si el original está en el móvil (`ArchivosExternos.estaEnElMovil`: proveedores locales o de gestores de archivos —«file» en el proveedor, sin nombres de nubes—). Libros: se borra el original. Cómics: se copian a la carpeta Libertalia y se borra el original (no contradice «los cómics no se copian»: no quedan dos). Si Android no deja borrar, se abre el selector en su carpeta (`EXTRA_INITIAL_URI`) con el aviso «Toca el archivo original…»; se comprueba tamaño y huella antes de borrar y nunca se borra nada de la carpeta Libertalia. En el emulador (Descargas) Android dejó borrar directamente; el camino del selector no se ha podido probar (en un móvil real, con Archivos de Google o Mis archivos de Samsung, es probable que haga falta).
  - **Obsidian (decisiones del usuario, 24/11):** subrayados, notas y tarjetas siguen en la base de datos interna (el usuario no quiere un único .md como almacén). Además, la app escribe archivos .md **en la bóveda de Obsidian del usuario** (la elige una vez con el selector de Android; dentro, una subcarpeta «Libertalia»), **uno por libro**, con cada subrayado en un recuadro `> [!quote]` (color, página) y la nota del usuario en otro `> [!note]`; las etiquetas como #etiqueta para que entren en el árbol de etiquetas de Obsidian. **En los dos sentidos:** la app también lee lo que se cambie en Obsidian (notas y etiquetas). Android no deja buscar la bóveda sola: «vincular» = elegir la carpeta. Pendiente de diseñar: cómo reconocer cada subrayado en el .md (id oculto), qué gana si se cambia lo mismo en los dos sitios (la fecha más reciente, como en las copias) y cuándo se leen los cambios (al abrir la app y al volver a ella).
- **Lista del usuario (28/11), en este orden:** (1) el teclado tapa «Mi nota» al editarla en la tarjeta; (2) probar «Buscar actualizaciones»; (3) al subrayar al principio o al final de una página el subrayado se extiende a otras páginas y «tiembla»; (4) en la ficha del libro (al pulsarlo), entre el autor y el estado, el número de páginas tal como las ve el lector, y un campo ISBN que se escribe a mano y se muestra debajo (cambia la decisión de la fase 6: sigue sin internet ni Open Library); (5) tarjetas numeradas por orden de aparición, con el formato «1. Título del libro» arriba de cada tarjeta; (6) **«Unir con otro subrayado»** (nombre elegido por el usuario): opción al tocar un subrayado, debajo de «Añadir etiqueta»; después se toca otro subrayado (se puede pasar de página) y los dos quedan en la misma tarjeta, por orden del texto: «fragmento 1 […] fragmento 2 […] fragmento 3». «Ir a la página» lleva al primer fragmento. **Dos subrayados en el mismo párrafo se unen solos** (aviso con «Separar»); (7) **modo oscuro de la interfaz** con ajuste «Claro / Oscuro / Como el móvil» (marrón oscuro y crema, sin negro ni blanco puros); (8) Obsidian; (9) voces.
- **Fase «Repositorios» descartada (el usuario, 24/11).** Se pensó buscar y descargar libros desde catálogos OPDS o APIs desde el botón +; se quitó. **La app no usa internet salvo para «Buscar actualizaciones».**
- **Buscar actualizaciones (decisión del usuario, 24/11, versión 0.2.0):** interruptor en Ajustes → «La app», **desactivado de entrada**. Activado, una vez al día al abrir la app (y con «Buscar ahora») pide a `api.github.com/repos/AWI-St/libertalia-reader/releases/latest` la última versión (sin enviar datos del usuario) y, si es más nueva que la instalada, avisa una sola vez por versión con «Descargar» (abre el APK de la versión en el navegador). Código en `actualizaciones/`. Por eso la app tiene ahora el permiso INTERNET (solo para esto; lo dicen «Acerca de» y el README). **Para cada versión nueva:** subir `versionCode` y `versionName`, generar el APK firmado y publicarlo en GitHub como versión (release) con etiqueta `vX.Y.Z` y el APK adjunto. Se hace con GitHub CLI (instalado con winget y con la sesión del usuario iniciada): `git tag -a vX.Y.Z` + `git push origin main vX.Y.Z` y luego `"C:\Program Files\GitHub CLI\gh.exe" release create vX.Y.Z entregas/Libertalia-Reader-X.Y.Z.apk --repo AWI-St/libertalia-reader --title ... --notes ...`. La 0.2.0 se publicó el 24/11. Probado en el emulador (28/11) con una versión de pruebas que decía ser la 0.1.9: «Buscar ahora» detectó la 0.2.0 y salió el aviso.
- Fase 11 — decisiones del usuario (23/11):
  - Botón «Escuchar» en la barra inferior del lector (solo libros). Lee desde la página que se ve, resalta la frase y pasa las páginas sola; panel con reproducir/pausar, frase anterior/siguiente, velocidad y parar. Voces de Android (Readium TTS), en el idioma del libro si el móvil la tiene.
  - **Sigue leyendo con la pantalla apagada o en otra app**, con la notificación de controles que exige Android mientras suena (también en la pantalla de bloqueo; desaparece al parar).
  - **PDF:** Readium no lee en voz alta el PDF original; «Escuchar» pasa al modo texto en la misma página y empieza a leer.
  - **Modo texto: se saltan las notas al pie** (solo el texto principal).
  - **El tiempo escuchando cuenta para la racha** y para el tiempo leído de la semana (también con la pantalla apagada).
- Decisión del usuario (fase 5): **la nube es para las copias de seguridad, no para leer.** Al añadir un cómic que está en una nube (proveedor que no es del propio móvil, `OrigenComic.estaEnLaNube`), aviso «Este cómic está en la nube» recomendando guardarlo en el móvil. Opción «Guardar en el móvil» en el menú del cómic: lo copia una vez a la carpeta Libertalia, usa esa copia (abre en ~1 s, sin datos ni conexión) y suelta el permiso de la nube. Los libros EPUB/PDF ya se copiaban siempre a la carpeta.
  - 5c (doble página, scroll vertical, recorte de márgenes, marcadores): hecho y probado.
    - Botones Página / Doble / Scroll en la barra inferior (`ModoComic`). Cada cómic recuerda el suyo (preferencias "comic", clave "modo_<id>"). Si no se ha elegido ninguno: doble página en horizontal y página a página en vertical. Pulsar el modo que ya se ve no cambia nada (no quita el automático).
    - Doble página: la portada (página 0) va sola; después pliegos de dos (1-2, 3-4...). En manga, la primera del par a la derecha. Ambas a la misma altura en un `Row` con `aspectRatio`; el zoom y `limitar` usan esa proporción. Etiqueta «Pág. X–Y de Z».
    - Scroll: `LazyColumn` a todo el ancho; página actual = la que ocupa el primer tercio. Las páginas se leen enteras (hasta 32 768 px) y se cortan en tiras de 2048 px (`tiras`), porque la tarjeta gráfica no dibuja imágenes tan altas. Sin zoom ni recorte (en los webtoons los blancos son parte de la historia). Lados y volumen desplazan un 85 % de la pantalla.
    - Recorte de márgenes blancos (`Recorte`): global, activado por defecto (preferencias "comic", clave "recortar"), botón de escuadras en la barra superior. Quita filas y columnas casi blancas (claridad ≥ 222, hasta un 1 % de manchas), como mucho un 20 % por lado, y deja un poco de aire.
    - Marcadores: cinta de la barra superior (llena y roja si la página está marcada; en doble página cuenta cualquiera de las dos) y lista de páginas marcadas (ir / quitar). Se guardan en la columna `marcapaginas` como lista JSON de números de página desde 1 («[3,12]»): sin migración.
    - Caché de páginas con clave por página, tamaño y recorte (`LruCache<String, List<Bitmap>>`).
  - APK de pruebas para el móvil del usuario en `entregas/Libertalia-Reader-prueba.apk` (carpeta fuera de Git; volver a copiarlo tras cada cambio). Pendiente ofrecerle crear ya la firma definitiva para no tener que desinstalar en la fase 14 (la contraseña la elige él).
  - Prueba: `python herramientas/crear_comics_prueba.py` crea `mareas-rojas-1.cbz` (manga con ComicInfo), `la-ciudad-sumergida-02.cbz` (tomo en el nombre), `tormenta-de-papel.cbr` (RAR 4 hecho a mano), `archivo-rar5.cbr` (para el aviso) y `comic-escaneado.pdf`.
- Decisión del usuario (fase 5, cambia la descripción original): **los cómics no se copian a la carpeta Libertalia** (pesan mucho; no deben ocupar espacio dos veces). Se abren siempre desde donde están y la app guarda solo sus datos (página, estado...).
  - Con «Añadir» se toma el permiso permanente del archivo (`takePersistableUriPermission`).
  - Con «Abrir con» el permiso suele ser temporal: se busca el mismo archivo en las carpetas con permiso (la de Libertalia...). Si no está, aviso «¿Dónde está este cómic?» que abre el selector de archivos en su carpeta (EXTRA_INITIAL_URI) para tocarlo una vez; se comprueba tamaño y huella. No se pide la carpeta: Android no deja usar «Download» entera con OPEN_DOCUMENT_TREE.
  - Los cómics fuera de la carpeta no se renombran ni se borran desde la app (`CarpetaLibertalia.contiene`).
  - Pendiente revisar en la fase 10: «incluir el archivo en la copia» y el aviso de archivos pesados, ahora que los cómics no están en la carpeta.
  - Si un cómic ya no se puede leer (permiso perdido, archivo movido), al tocarlo sale el mismo aviso para volver a elegirlo (nunca se cierra la app: `ServicioLectura` captura la excepción de Readium).
  - El selector del botón + acepta cualquier archivo (`*/*`): cada nube llama distinto a los CBR (en Drive salían en gris). El importador comprueba el contenido.
- Decisión del usuario (fase 5): los cómics y mangas tienen **su propio estante «Cómics y manga»** por defecto (siempre, sin crearlo). Los estantes Leyendo/Pendientes/Terminados son solo para libros; con un filtro de estado, se ven también los cómics de ese estado en su estante.
  - Petición del usuario (hecho en 5b): los cómics aparecen **también** en los estantes Leyendo y Terminados (además de en «Cómics y manga»); se distinguen por verse de frente, como revistas. Los pendientes solo en su estante.
- Decisiones del usuario (fase 4, cambian la descripción original):
  - La barra superior del lector (volver, título, autor, buscar) está **siempre visible**. Solo se pliega/despliega la barra inferior, con la pestaña o con un toque en el centro de la página.
  - El texto ocupa siempre el mismo hueco (bajo la barra superior y encima de la pestañita) y **nunca se reordena** al plegar/desplegar: la barra inferior desplegada pasa por encima del texto.
  - **Nunca porcentaje** en el lector: en EPUB y PDF se muestra «Capítulo · Pág. X de Y» con las páginas del lector. La barra de progreso funciona con esas páginas (la última página la lleva al final).
    - PDF: páginas reales del archivo.
    - EPUB (decisión del usuario en 4b): una página = una pantalla, así que con letra más grande hay más páginas. Cada capítulo abierto se mide (JavaScript en el WebView de Readium: ancho total / ancho de pantalla); los no medidos se estiman por su número de letras. Las medidas se guardan por libro y por «firma» (tamaño, tipografía, márgenes, interlineado, tamaño de pantalla) en las preferencias "paginas", así que el total es exacto al volver a abrir el libro. La primera vez el total puede corregirse al ir leyendo.
    - La posición que se guarda sigue siendo el Locator exacto de Readium.
  - Tamaño de letra del panel Aa: de 0,75 a 1,625 (el usuario pidió bajar el máximo al 75 % del recorrido anterior).
  - **Ajustes de texto por libro** (decisión del usuario): tamaño, tipografía, márgenes e interlineado son de cada libro (columna `ajustesTexto` en Room, base de datos v4). Tema, brillo e interruptores son de toda la app. Un libro que se abre por primera vez toma los últimos ajustes usados y se los queda guardados en ese momento.
  - **Marcapáginas** (decisión del usuario): opción «Marcapáginas» en el menú al seleccionar texto (EPUB). Uno por libro (poner otro lo mueve), columna `marcapaginas` (Locator JSON). Se dibuja como una cinta roja en el margen izquierdo (plantilla de decoración propia de Readium). Al abrir el libro se va siempre a la última posición leída (se sigue guardando al avanzar). Botón «Marcapáginas» en la barra inferior: pide confirmación y lleva a la frase marcada.
  - El menú de selección de Readium sustituye al del sistema: de momento tiene «Marcapáginas» y «Copiar». En la fase 7 se añaden colores de subrayado, nota, diccionario y traducir.
  - **Modo texto para PDF** (decisión del usuario, función nueva que no estaba en la descripción): los PDF hechos para imprimir se ven con letra diminuta en el móvil. Habrá un «modo texto» que saca el texto del PDF y lo muestra como un EPUB (tamaño de letra, Literata, temas, páginas), con un botón para cambiar entre el PDF original y su texto. Pierde la maquetación (columnas, imágenes, notas) y puede tener errores. Se hará como **paso 4c**, después del panel Aa (4b) y antes de los cómics.
    - Notas al pie con fondo distinto (petición del usuario): detectar las líneas de nota (abajo de la página, letra más pequeña que la del cuerpo, empiezan por número o llamada) y mostrarlas en un recuadro con otro fondo (crema algo más oscuro) y letra algo menor. Quitar también números de página y cabeceras/pies repetidos. Es detección por pistas: avisar de que puede fallar en algún PDF. En EPUB, aplicar el mismo estilo a las notas marcadas como tales (`epub:type="footnote"`, `aside`).
    - Técnica prevista: PdfBox-Android (com.tom-roush:pdfbox-android, licencia Apache 2.0) para extraer el texto, porque el motor de PDF de Readium (PdfiumAndroid) no sabe extraerlo. Convertir el texto en un EPUB interno (un capítulo por página o grupo de páginas) y abrirlo con el lector de EPUB de Readium, para tener gratis tipografías, temas, búsqueda, subrayados y tarjetas. Mantener la correspondencia de páginas para pasar de un modo a otro sin perder el sitio.
  - Al añadir un libro sin título dentro, el título (y el autor, si el nombre es «Autor - Título») se saca del nombre del archivo, cambiando `+` y `_` por espacios.
  - Fase 6: sin página en papel (decisión del usuario). Los widgets (fase 9) tampoco deben mostrar porcentaje.
- Decisiones del usuario (fase 2):
  - Al añadir un libro, si ya hay en la carpeta un archivo con el mismo contenido (huella), se reutiliza: nunca se duplican archivos.
  - «Eliminar libro» no borra sus datos: marca `eliminado = true`. Al volver a añadir el mismo libro (misma huella), recupera página, estado, estanterías, notas y tarjetas.
  - Icono circular en la esquina inferior derecha de la portada: tic verde (terminado), libro abierto amarillo (leyendo), nada (pendiente).
  - Dentro de cada estante: leyendo delante, pendientes en medio, terminados al final.
  - Las estanterías propias se pueden ordenar, renombrar y borrar (botón junto a su título).
- Cada cambio en la estructura de la base de datos sube la versión y añade una migración: nunca borrar los datos del usuario.
- Los libros de prueba ya no van dentro de la app: están en `herramientas/libros-prueba/` y se copian al emulador (carpeta Download) para añadirlos.

## Entorno y versiones

- Android Studio admite como máximo **AGP 9.4.x**: no subir el Android Gradle Plugin por encima sin comprobarlo (con 9.5 Android Studio da error de incompatibilidad).
- Kotlin 2.4.x (Readium 3.4 está compilado con Kotlin 2.4). Gradle 9.6. compileSdk 37.1 (lo pide Compose 1.13), minSdk 26.
- Readium 3.4.0 necesita *core library desugaring* activado.
- El motor de PDF de Readium (com.github.marain87) viene de JitPack, limitado a ese grupo en `settings.gradle.kts`.
- Compilar desde la terminal: `JAVA_HOME` = `C:\Program Files\Android\Android Studio\jbr`, y `./gradlew :app:assembleDebug`.
- Emulador: `Medium_Phone_API_37.0`. Lo abre el usuario desde Android Studio (▶ Run). No arrancarlo con `-no-snapshot-save`: al cerrarse se pierde todo lo hecho en esa sesión (archivos copiados, libros añadidos).
- En Git Bash, usar `MSYS_NO_PATHCONV=1` con `adb push`/`adb shell` para que no convierta rutas como `/sdcard/...`.
- Los libros de prueba se generan con `python herramientas/crear_libros_prueba.py` (Quijote, dominio público).

## Notas técnicas a tener en cuenta

- **Carpeta Documentos/Libertalia:** desde Android 11 una app no puede leer libremente una carpeta pública tras reinstalarse. Plan: la primera vez se pide al usuario que elija/cree la carpeta con el selector de Android (`ACTION_OPEN_DOCUMENT_TREE`) y se guarda el permiso permanente. Así los archivos sobreviven a la desinstalación y la app puede volver a leerlos.
- **CBR:** junrar solo abre RAR 4 y anteriores; los CBR en formato RAR 5 deben mostrar un aviso claro.
- **Detalles sacados de los bocetos** (completan la descripción):
  - Biblioteca: fila de filtros Todos · Leyendo · Pendientes · Terminados bajo el título; botón añadir redondo marrón.
  - Subrayados en 4 colores: amarillo, verde, azul y rosa (pastel).
  - Biblioteca vacía: título "Tu estantería está vacía", texto extra sobre las tarjetas y nota sobre "Abrir con".
  - Al subrayar aparece un aviso abajo: "Al subrayar se crea una tarjeta con la frase completa" con botón "Ver".
  - El menú al mantener pulsado del boceto no muestra el interruptor de copia automática, pero la descripción sí lo pide: se incluye.
  - En los bocetos la letra es Lora; en la app será Literata.
- **ML Kit (OCR, futuro):** es gratuito y funciona sin conexión, pero no es software libre (términos de Google). Revisarlo cuando llegue esa fase.

---

# Descripción completa de la app

Filosofía: gratis, privada y propia. Funciona entera en el móvil: sin cuentas, sin registro, sin servidores, sin publicidad, sin iniciar sesión en ningún servicio y sin recoger datos de nadie. Todo se guarda en el propio dispositivo, y para cambiar de móvil se usan copias de seguridad que el usuario guarda donde quiera.

Nombre e identidad:
- Nombre de la app: Libertalia Reader (igual en todos los idiomas; no se traduce).
- Nombre de paquete: org.libertalia.reader
- Extensión de las copias de seguridad: .libertalia
- Carpeta en el móvil: Documentos/Libertalia
- Icono: bocetos/icono-libertalia.svg (decisión del usuario, 23/11, sustituye a la brújula: un libro abierto delante y un timón de barco asomando detrás, en crema sobre fondo marrón #7A4A2A). El SVG se redibujó (23/11) a partir de la imagen que pasó el usuario (la brújula queda en el historial de Git). Icono adaptativo ya hecho (23/11, lo pidió el usuario antes de la fase 14): `mipmap-anydpi-v26/ic_launcher(_round).xml` con fondo `@color/icono_fondo`, primer plano `drawable/ic_launcher_foreground.xml` (dibujo del boceto escalado al 60 % para la zona segura, líneas más gruesas, sin líneas de texto) y `drawable/ic_launcher_monocromo.xml` (iconos temáticos: páginas rellenas con hueco en el lomo). Probado en el emulador. En la fase 14 queda el APK firmado.

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
14. Icono de la app a partir de bocetos/icono-libertalia.svg: icono adaptativo de Android con el fondo marrón #7A4A2A y el libro abierto con el timón en primer plano dentro de la zona segura, con líneas algo más gruesas y sin las líneas de texto del libro (en tamaños pequeños no se verían), más una versión monocroma para los iconos temáticos de Android 13. Después, generar el APK firmado para instalarlo en mi móvil.

Para más adelante (no construir todavía, pero tenlo en cuenta al diseñar los datos y el lector):
- Libros escaneados (OCR): si un PDF o EPUB de texto tiene páginas que son solo imágenes, ofrecer "Reconocer texto" usando ML Kit Text Recognition en el propio móvil, sin servidores y sin coste. Procesar en segundo plano con barra de progreso, con opción de hacerlo solo mientras el móvil carga o empezando por las páginas que se van leyendo. Guardar el texto reconocido y la posición de cada palabra, y colocar una capa de texto invisible sobre la imagen para poder seleccionar, subrayar, buscar y crear tarjetas igual que en un libro con texto. Avisar de que puede contener errores; el texto de las tarjetas es editable para corregirlos.
- Compartir tarjetas en redes sociales: desde cada tarjeta, generar una imagen con la cita, el título y el autor del libro, y compartirla con el menú de compartir de Android (WhatsApp, Instagram, X, etc.). El usuario elige el fondo: varios diseños de la app (con el estilo crema por defecto), la portada del libro difuminada o una foto propia de su galería o cámara. Sobre fotos, añadir una capa semitransparente para que el texto se lea bien, y permitir elegir el formato (cuadrado, vertical para historias). También compartir solo el texto de la cita. La nota personal nunca se incluye salvo que el usuario lo elija.
- Más formatos: TXT, CB7 y carpetas de imágenes como cómic.

Trabajaremos una fase cada vez. Cuando una funcione, haz un commit de Git con un mensaje en español.
