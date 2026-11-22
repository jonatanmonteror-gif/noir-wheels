package com.wolfwheels.app.data

/** What's new in each version (Settings → Novedades). Add one entry per version. */
object Changelog {
    data class Entry(val version: String, val items: List<String>)

    val entries = listOf(
        Entry("Inicio, logros y más", listOf(
            "Inicio renovado: resumen de Mi garaje, lista para la tienda, coche al azar, vistos recientemente y nuevos modelos 2026.",
            "¿Has olvidado la contraseña? Te llega un código al correo para poner una nueva.",
            "Logros en Mi garaje y el valor de tu garaje (próximamente).",
            "Copia de seguridad: guarda tu garaje en un archivo y recupéralo cuando quieras (Ajustes).",
            "Comparte cualquier coche como una imagen con el estilo de Wolf Wheels.",
            "El escáner avisa si el coche está en tu lista de deseos y descifra el código de la base (cuándo se fabricó).",
            "Las fichas que ya has visto se abren al momento y sin conexión.",
            "Si la app se cierra sola, al volver te enseña un informe para arreglarlo.",
        )),
        Entry("Mi garaje", listOf(
            "«Mi colección» ahora se llama Mi garaje, con su botón en el inicio.",
            "Las pestañas, la búsqueda, el orden y los garajes van dentro de «Personalizar vista», plegada para que todo quede más limpio.",
            "Los correos de la cuenta llegan con la imagen de Wolf Wheels.",
        )),
        Entry("Avisos y cuentas verificadas", listOf(
            "Aviso al abrir la app cuando hay una versión nueva, con «Actualizar» o «Luego».",
            "Al crear la cuenta te llega un código a tu email para verificarla.",
            "Las columnas (1, 2 o 3) se eligen ahora en Ajustes → Vista: la app queda más limpia.",
            "En cada modelo se ven 15 fotos y el botón «Ver todas las fotos».",
            "Mi colección renovada: resumen de tu garaje, botones Escanear y Exportar, pestañas Tengo / Quiero / Repes / Series y separación por años al ordenar por año.",
        )),
        Entry("Escáner, filtros y colección", listOf(
            "Escanear tarjeta: haz una foto al blíster (o escribe el número de juguete, p. ej. HKG43) y la app abre el coche y la versión exacta. Te avisa si ya lo tienes.",
            "Ver los coches en 1, 2 o 3 columnas (se recuerda en todas las listas).",
            "Versiones: filtra por color y por Treasure Hunt / Super Treasure Hunt. Las TH y STH llevan su marca.",
            "Listas de series, diseñadores…: filtra por nombre y por año, y ordena A-Z, recientes o antiguos.",
            "Mi colección: cantidad (repetidos para cambiar), conservación, precio pagado, garajes, notas y tu propia foto.",
            "Pestañas Tengo, Quiero, Repetidos y Series (cuántos tienes de cada serie y cuántos te faltan).",
            "Exportar la colección a Excel (CSV) con el botón de compartir.",
        )),
        Entry("Todo en español", listOf(
            "La historia, las curiosidades, los datos de la tarjeta y los datos de cada versión (color, decoración, base, interior, notas…) salen traducidos al español.",
            "Cada texto se traduce una sola vez y se guarda: la próxima vez sale ya traducido al momento, incluso sin conexión.",
            "La primera vez que abres la app se descarga el idioma (unos 30 MB).",
        )),
        Entry("Colección y cuentas", listOf(
            "Toca un dato (serie de debut, diseñador, año, ruedas, serie de una versión…) y verás todos los coches de esa serie, de ese diseñador, etc.",
            "Mi colección: marca cada versión con «La tengo» o «La quiero». Con contador de coches, modelos y deseos.",
            "Cuenta de usuario: crea tu cuenta para guardar la colección y verla en todos tus aparatos.",
            "Fotos para las versiones sin foto en la wiki: busca otra foto de esa versión y, si no hay, la de un anuncio de eBay.",
        )),
        Entry("Actualizaciones", listOf(
            "Las actualizaciones se descargan bien también desde la app antigua (Noir Wheels).",
            "Mensajes de error más claros al actualizar: ya no dice «sin conexión» cuando el problema es otro.",
        )),
        Entry("Wolf Wheels", listOf(
            "Nuevo nombre e imagen: Wolf Wheels, con logo propio y diseño de carreras.",
            "Versiones de cada coche: elige una para ver su foto y todos sus datos (año, serie, color, decoración, base, interior, ruedas, nº de juguete, país y notas).",
            "Filtra las versiones por tipo y ordénalas de más recientes a más antiguas.",
            "Ficha del modelo con nº de versiones, año de debut y última versión.",
            "Historia completa del modelo, datos de la tarjeta y curiosidades.",
        )),
        Entry("Primera versión", listOf(
            "Primera versión.",
            "Buscador de modelos y colecciones: novedades 2026, 2025, 2024, Treasure Hunts, Super Treasure Hunts y Monster Trucks.",
            "Ficha de cada modelo con sus datos y todas las fotos de sus versiones.",
            "Visor de fotos a pantalla completa: desliza para pasar y pellizca o toca dos veces para ampliar.",
            "Actualizaciones desde la propia app.",
        )),
    )
}
