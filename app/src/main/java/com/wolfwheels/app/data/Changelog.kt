package com.wolfwheels.app.data

/** What's new in each version (Settings → Novedades). Add one entry per version. */
object Changelog {
    data class Entry(val version: String, val items: List<String>)

    val entries = listOf(
        Entry("1.0.5", listOf(
            "Toca un dato (serie de debut, diseñador, año, ruedas, serie de una versión…) y verás todos los coches de esa serie, de ese diseñador, etc.",
            "Mi colección: marca cada versión con «La tengo» o «La quiero». Con contador de coches, modelos y deseos.",
            "Cuenta de usuario: crea tu cuenta para guardar la colección y verla en todos tus aparatos.",
            "Fotos para las versiones sin foto en la wiki: busca otra foto de esa versión y, si no hay, la de un anuncio de eBay.",
        )),
        Entry("1.0.4", listOf(
            "Las actualizaciones se descargan bien también desde la app antigua (Noir Wheels).",
            "Mensajes de error más claros al actualizar: ya no dice «sin conexión» cuando el problema es otro.",
        )),
        Entry("1.0.3", listOf(
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
