package com.wolfwheels.app.data

/** What's new in each version (Settings → Novedades). Add one entry per version. */
object Changelog {
    data class Entry(val version: String, val items: List<String>)

    val entries = listOf(
        Entry("1.0.2", listOf(
            "Nuevo nombre e imagen: Wolf Wheels, con logo propio y diseño de carreras.",
            "Versiones de cada coche: elige una para ver su foto y todos sus datos (año, serie, color, decoración, base, interior, ruedas, nº de juguete, país y notas).",
            "Filtra las versiones por tipo y ordénalas de más recientes a más antiguas.",
            "Ficha del modelo con nº de versiones, año de debut y última versión.",
            "Historia completa del modelo, datos de la tarjeta y curiosidades.",
        )),
        Entry("1.0.1", listOf(
            "Primera versión.",
            "Buscador de modelos y colecciones: novedades 2026, 2025, 2024, Treasure Hunts, Super Treasure Hunts y Monster Trucks.",
            "Ficha de cada modelo con sus datos y todas las fotos de sus versiones.",
            "Visor de fotos a pantalla completa: desliza para pasar y pellizca o toca dos veces para ampliar.",
            "Actualizaciones desde la propia app.",
        )),
    )
}
