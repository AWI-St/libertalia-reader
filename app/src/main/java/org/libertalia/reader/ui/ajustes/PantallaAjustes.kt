package org.libertalia.reader.ui.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.libertalia.reader.R
import org.libertalia.reader.actualizaciones.Actualizaciones
import org.libertalia.reader.actualizaciones.BusquedaActualizacion
import org.libertalia.reader.actualizaciones.DialogoActualizacion
import org.libertalia.reader.comic.ModoComic
import org.libertalia.reader.copias.InfoCopiaAutomatica
import org.libertalia.reader.lector.TipografiaLector
import org.libertalia.reader.ui.copias.textoFrecuencia
import org.libertalia.reader.ui.tema.familiaDe
import java.text.DateFormat
import java.util.Date

/** Qué ajuste se está eligiendo en un cuadro de opciones. */
private enum class Eleccion { IDIOMA, ASPECTO, TIPOGRAFIA, LIMITE, ORIGINALES, PESADOS, MODO_COMIC }

/**
 * Pantalla Ajustes (boceto 12): aviso de privacidad, estadísticas rápidas (sin la racha, que
 * vive en el widget), «Copias y cambio de móvil», los ajustes de la app, los del lector de
 * cómics, «Liberar espacio» y «Acerca de».
 */
@Composable
fun PantallaAjustes(
    alVolver: () -> Unit,
    alExportar: () -> Unit,
    alImportar: () -> Unit,
    infoCopia: InfoCopiaAutomatica,
    alCopiaAutomatica: () -> Unit,
    alLiberarEspacio: () -> Unit,
    alAcercaDe: () -> Unit,
    alEstadisticas: () -> Unit,
) {
    val estado: AjustesViewModel = viewModel()
    var eleccion by rememberSaveable { mutableStateOf<Eleccion?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fondo_app))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = alVolver) {
                Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
            }
            Text(
                stringResource(R.string.ajustes_titulo),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // Aviso: todo está solo en este móvil.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colorResource(R.color.borde).copy(alpha = 0.55f))
                .padding(16.dp),
        ) {
            Icon(painterResource(R.drawable.ic_candado), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.ajustes_aviso_privacidad), style = MaterialTheme.typography.bodyMedium)
        }

        // Estadísticas rápidas.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth(),
        ) {
            Cifra(
                valor = estado.terminadosEsteAnio?.toString() ?: "…",
                texto = stringResource(R.string.ajustes_libros_este_anio),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = alEstadisticas),
            )
            Cifra(
                valor = estado.segundosSemana?.let { textoTiempo(it) } ?: "…",
                texto = stringResource(R.string.ajustes_esta_semana),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = alEstadisticas),
            )
        }

        Bloque(stringResource(R.string.ajustes_copias)) {
            FilaAjuste(R.drawable.ic_exportar, stringResource(R.string.ajustes_exportar), alPulsar = alExportar)
            HorizontalDivider(color = colorResource(R.color.borde))
            FilaAjuste(R.drawable.ic_guardar_en, stringResource(R.string.ajustes_importar), alPulsar = alImportar)
            HorizontalDivider(color = colorResource(R.color.borde))
            // «copia.libertalia · cada 3 días · última: 23 nov 2026» (o en rojo, si falló).
            val detalleCopia = when {
                infoCopia.uri == null -> stringResource(R.string.copia_sin_configurar)
                infoCopia.falla -> stringResource(R.string.copia_fallo_titulo)
                else -> listOfNotNull(
                    infoCopia.nombre,
                    textoFrecuencia(infoCopia.dias),
                    infoCopia.fechaUltimaCorrecta?.let {
                        stringResource(R.string.copia_ultima_corta, AjustesApp.fecha(LocalContext.current, it, DateFormat.MEDIUM))
                    },
                ).joinToString(" · ")
            }
            FilaAjuste(
                R.drawable.ic_reloj,
                stringResource(R.string.copia_titulo),
                detalle = detalleCopia,
                detalleError = infoCopia.falla,
                alPulsar = alCopiaAutomatica,
            )
        }

        Bloque(stringResource(R.string.ajustes_bloque_ajustes)) {
            FilaValor(stringResource(R.string.ajustes_idioma), nombreIdioma(estado.idioma)) { eleccion = Eleccion.IDIOMA }
            HorizontalDivider(color = colorResource(R.color.borde))
            FilaValor(stringResource(R.string.ajustes_aspecto), nombreAspecto(AjustesApp.aspecto)) { eleccion = Eleccion.ASPECTO }
            HorizontalDivider(color = colorResource(R.color.borde))
            FilaValor(stringResource(R.string.ajustes_tipografia), estado.tipografia.nombre) { eleccion = Eleccion.TIPOGRAFIA }
            HorizontalDivider(color = colorResource(R.color.borde))
            FilaAjuste(
                icono = null,
                texto = stringResource(R.string.ajustes_carpeta),
                detalle = (estado.carpeta ?: "—") + "\n" + stringResource(R.string.ajustes_carpeta_nota),
                flecha = false,
                alPulsar = {},
            )
            HorizontalDivider(color = colorResource(R.color.borde))
            FilaValor(stringResource(R.string.ajustes_limite_pesado), stringResource(R.string.ajustes_mb, estado.limitePesadoMb)) {
                eleccion = Eleccion.LIMITE
            }
            HorizontalDivider(color = colorResource(R.color.borde))
            FilaValor(stringResource(R.string.ajustes_originales), textoTriple(estado.borrarOriginales, R.string.ajustes_borrar, R.string.ajustes_conservar)) {
                eleccion = Eleccion.ORIGINALES
            }
            HorizontalDivider(color = colorResource(R.color.borde))
            FilaValor(stringResource(R.string.ajustes_pesados), textoTriple(estado.incluirPesados, R.string.ajustes_incluir, R.string.ajustes_solo_datos)) {
                eleccion = Eleccion.PESADOS
            }
            HorizontalDivider(color = colorResource(R.color.borde))
            FilaAjuste(R.drawable.ic_eliminar, stringResource(R.string.ajustes_liberar_espacio), detalle = stringResource(R.string.ajustes_liberar_espacio_nota), alPulsar = alLiberarEspacio)
        }

        Bloque(stringResource(R.string.ajustes_bloque_comics)) {
            FilaValor(stringResource(R.string.ajustes_modo_comic), textoModo(estado.modoComic)) { eleccion = Eleccion.MODO_COMIC }
            HorizontalDivider(color = colorResource(R.color.borde))
            FilaInterruptor(stringResource(R.string.ajustes_recortar), estado.recortarComics, estado::cambiarRecortarComics)
        }

        Bloque(stringResource(R.string.ajustes_bloque_app)) {
            // Buscar actualizaciones en GitHub: lo único para lo que la app usa internet.
            FilaInterruptor(
                stringResource(R.string.ajustes_actualizaciones),
                estado.buscarActualizaciones,
                estado::cambiarBuscarActualizaciones,
                detalle = stringResource(R.string.ajustes_actualizaciones_nota),
            )
            if (estado.buscarActualizaciones) {
                HorizontalDivider(color = colorResource(R.color.borde))
                val busqueda = estado.busqueda
                FilaAjuste(
                    null,
                    stringResource(R.string.ajustes_buscar_ahora),
                    detalle = textoBusqueda(busqueda),
                    detalleError = busqueda == BusquedaActualizacion.Error,
                    flecha = busqueda != BusquedaActualizacion.Buscando,
                    alPulsar = estado::buscarAhora,
                )
            }
            HorizontalDivider(color = colorResource(R.color.borde))
            FilaAjuste(null, stringResource(R.string.estadisticas_titulo), alPulsar = alEstadisticas)
            HorizontalDivider(color = colorResource(R.color.borde))
            FilaAjuste(null, stringResource(R.string.acerca_titulo), alPulsar = alAcercaDe)
        }
    }

    // Hay una versión nueva en GitHub.
    (estado.busqueda as? BusquedaActualizacion.Nueva)?.let { DialogoActualizacion(it.version, alCerrar = estado::avisoVisto) }

    // Cuadros para elegir.
    when (eleccion) {
        Eleccion.IDIOMA -> DialogoOpciones(
            titulo = stringResource(R.string.ajustes_idioma),
            opciones = AjustesApp.IDIOMAS.map { it to nombreIdioma(it) },
            elegida = estado.idioma,
            nota = stringResource(R.string.ajustes_idioma_nota),
            alElegir = { eleccion = null; estado.cambiarIdioma(it) },
            alCerrar = { eleccion = null },
        )
        Eleccion.ASPECTO -> {
            val contexto = androidx.compose.ui.platform.LocalContext.current
            DialogoOpciones(
                titulo = stringResource(R.string.ajustes_aspecto),
                opciones = listOf(AspectoApp.SISTEMA, AspectoApp.CLARO, AspectoApp.OSCURO).map { it to nombreAspecto(it) },
                elegida = AjustesApp.aspecto,
                alElegir = { eleccion = null; AjustesApp.cambiarAspecto(contexto, it) },
                alCerrar = { eleccion = null },
            )
        }
        Eleccion.TIPOGRAFIA -> DialogoOpciones(
            titulo = stringResource(R.string.ajustes_tipografia),
            opciones = TipografiaLector.entries.map { it to it.nombre },
            elegida = estado.tipografia,
            vistaPrevia = true,
            alElegir = { eleccion = null; estado.cambiarTipografia(it) },
            alCerrar = { eleccion = null },
        )
        Eleccion.LIMITE -> DialogoOpciones(
            titulo = stringResource(R.string.ajustes_limite_pesado),
            opciones = AjustesViewModel.LIMITES_PESADO.map { it to stringResource(R.string.ajustes_mb, it) },
            elegida = estado.limitePesadoMb,
            nota = stringResource(R.string.ajustes_limite_pesado_nota),
            alElegir = { eleccion = null; estado.cambiarLimitePesado(it) },
            alCerrar = { eleccion = null },
        )
        Eleccion.ORIGINALES -> DialogoOpciones(
            titulo = stringResource(R.string.ajustes_originales),
            opciones = listOf(null, true, false).map { it to textoTriple(it, R.string.ajustes_borrar, R.string.ajustes_conservar) },
            elegida = estado.borrarOriginales,
            alElegir = { eleccion = null; estado.cambiarBorrarOriginales(it) },
            alCerrar = { eleccion = null },
        )
        Eleccion.PESADOS -> DialogoOpciones(
            titulo = stringResource(R.string.ajustes_pesados),
            opciones = listOf(null, true, false).map { it to textoTriple(it, R.string.ajustes_incluir, R.string.ajustes_solo_datos) },
            elegida = estado.incluirPesados,
            alElegir = { eleccion = null; estado.cambiarIncluirPesados(it) },
            alCerrar = { eleccion = null },
        )
        Eleccion.MODO_COMIC -> DialogoOpciones(
            titulo = stringResource(R.string.ajustes_modo_comic),
            opciones = listOf(null, ModoComic.PAGINA, ModoComic.DOBLE, ModoComic.SCROLL).map { it to textoModo(it) },
            elegida = estado.modoComic,
            alElegir = { eleccion = null; estado.cambiarModoComic(it) },
            alCerrar = { eleccion = null },
        )
        null -> Unit
    }
}

/** «4 h 20» o «35 min». */
@Composable
fun textoTiempo(segundos: Long): String {
    val minutos = segundos / 60
    return if (minutos >= 60) stringResource(R.string.tiempo_horas, minutos / 60, minutos % 60)
    else stringResource(R.string.tiempo_minutos, minutos)
}

/** «Como el móvil», «Claro» u «Oscuro». */
@Composable
private fun nombreAspecto(aspecto: AspectoApp): String = stringResource(
    when (aspecto) {
        AspectoApp.SISTEMA -> R.string.aspecto_sistema
        AspectoApp.CLARO -> R.string.aspecto_claro
        AspectoApp.OSCURO -> R.string.aspecto_oscuro
    },
)

/** El nombre de cada idioma en su propio idioma (null = el del sistema). */
@Composable
private fun nombreIdioma(etiqueta: String?): String = stringResource(
    when (etiqueta) {
        "es" -> R.string.idioma_es
        "gl" -> R.string.idioma_gl
        "eu" -> R.string.idioma_eu
        "ca" -> R.string.idioma_ca
        "en" -> R.string.idioma_en
        "fr" -> R.string.idioma_fr
        else -> R.string.idioma_sistema
    },
)

/** «Preguntar» (null), o el texto del sí o del no. */
@Composable
private fun textoTriple(valor: Boolean?, si: Int, no: Int): String =
    stringResource(if (valor == null) R.string.ajustes_preguntar else if (valor) si else no)

@Composable
private fun textoModo(modo: ModoComic?): String = stringResource(
    when (modo) {
        null -> R.string.ajustes_modo_automatico
        ModoComic.PAGINA -> R.string.comic_modo_pagina
        ModoComic.DOBLE -> R.string.comic_modo_doble
        ModoComic.SCROLL -> R.string.comic_modo_scroll
    },
)

/** Una cifra de las estadísticas rápidas, en su recuadro. */
@Composable
private fun Cifra(valor: String, texto: String, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(forma)
            .background(colorResource(R.color.fondo_tarjeta))
            .border(1.dp, colorResource(R.color.borde), forma)
            .padding(16.dp),
    ) {
        Text(valor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Un cuadro con una lista de opciones (una sola elegida). */
@Composable
private fun <T> DialogoOpciones(
    titulo: String,
    opciones: List<Pair<T, String>>,
    elegida: T,
    alElegir: (T) -> Unit,
    alCerrar: () -> Unit,
    nota: String? = null,
    vistaPrevia: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = colorResource(R.color.fondo_tarjeta),
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                opciones.forEach { (valor, texto) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.RadioButton) { alElegir(valor) },
                    ) {
                        RadioButton(
                            selected = valor == elegida,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(12.dp))
                        // En las tipografías, cada nombre se escribe con su propia letra.
                        val familia = if (vistaPrevia && valor is TipografiaLector) familiaDe(valor) else null
                        Text(
                            texto,
                            style = if (familia != null) MaterialTheme.typography.bodyLarge.copy(fontFamily = familia) else MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                if (nota != null) {
                    Spacer(Modifier.heightIn(min = 8.dp))
                    Text(nota, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.boton_cancelar)) } },
    )
}

/** Un bloque de ajustes: recuadro crema con título en versalitas marrones. */
@Composable
fun Bloque(titulo: String, contenido: @Composable () -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 18.dp)
            .fillMaxWidth()
            .clip(forma)
            .background(colorResource(R.color.fondo_tarjeta))
            .border(1.dp, colorResource(R.color.borde), forma),
    ) {
        Text(
            titulo.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        contenido()
    }
}

/** Una fila de ajustes: icono, texto (y detalle debajo) y flecha. */
@Composable
fun FilaAjuste(
    icono: Int?,
    texto: String,
    detalle: String? = null,
    detalleError: Boolean = false,
    flecha: Boolean = true,
    alPulsar: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = flecha, onClick = alPulsar)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (icono != null) {
            Icon(painterResource(icono), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(texto, style = MaterialTheme.typography.titleMedium)
            if (detalle != null) {
                Text(
                    detalle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (detalleError) colorResource(R.color.rojo) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (flecha) {
            Spacer(Modifier.width(8.dp))
            Icon(
                painterResource(R.drawable.ic_siguiente),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** «Idioma ........ Castellano ›» (el valor a la derecha; si no cabe, pasa debajo). */
@Composable
private fun FilaValor(texto: String, valor: String, alPulsar: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alPulsar)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(texto, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            painterResource(R.drawable.ic_siguiente),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Lo que dice la fila «Buscar ahora»: la versión instalada o cómo ha ido la búsqueda. */
@Composable
private fun textoBusqueda(busqueda: BusquedaActualizacion?): String {
    val actual = Actualizaciones.versionActual(androidx.compose.ui.platform.LocalContext.current)
    return when (busqueda) {
        null -> stringResource(R.string.actualizacion_instalada, actual)
        BusquedaActualizacion.Buscando -> stringResource(R.string.actualizacion_buscando)
        BusquedaActualizacion.AlDia -> stringResource(R.string.actualizacion_al_dia, actual)
        BusquedaActualizacion.Error -> stringResource(R.string.actualizacion_error)
        is BusquedaActualizacion.Nueva -> stringResource(R.string.actualizacion_hay_nueva, busqueda.version.version)
        is BusquedaActualizacion.Avisada -> stringResource(R.string.actualizacion_hay_nueva, busqueda.version.version)
    }
}

@Composable
private fun FilaInterruptor(texto: String, activado: Boolean, alCambiar: (Boolean) -> Unit, detalle: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { alCambiar(!activado) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(texto, style = MaterialTheme.typography.titleMedium)
            if (detalle != null) {
                Text(detalle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = activado,
            onCheckedChange = alCambiar,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
