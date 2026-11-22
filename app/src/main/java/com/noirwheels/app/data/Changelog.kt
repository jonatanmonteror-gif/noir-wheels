package com.noirwheels.app.data

/** What's new in each version (Settings → Novedades). Add one entry per version. */
object Changelog {
    data class Entry(val version: String, val items: List<String>)

    val entries = listOf(
        Entry("1.0", listOf(
            "Primera versión de Noir Wheels.",
            "Buscador de modelos y colecciones: novedades 2026, 2025, 2024, Treasure Hunts, Super Treasure Hunts y Monster Trucks.",
            "Ficha de cada modelo con sus datos y todas las fotos de sus versiones.",
            "Visor de fotos a pantalla completa: desliza para pasar y pellizca o toca dos veces para ampliar.",
            "Actualizaciones desde la propia app.",
        )),
    )
}
