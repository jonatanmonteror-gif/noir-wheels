package com.wolfwheels.app.data

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) { sp = ctx.getSharedPreferences("wolfwheels", Context.MODE_PRIVATE) }

    /** Last list shown: "search:<text>" or "cat:<index>". */
    var lastList: String
        get() = sp.getString("last_list", "cat:0") ?: "cat:0"
        set(v) = sp.edit().putString("last_list", v).apply()

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v).apply()
    var userName: String
        get() = sp.getString("user_name", "") ?: ""
        set(v) = sp.edit().putString("user_name", v).apply()
    var userEmail: String
        get() = sp.getString("user_email", "") ?: ""
        set(v) = sp.edit().putString("user_email", v).apply()
    var lastSync: Long
        get() = sp.getLong("last_sync", 0L)
        set(v) = sp.edit().putLong("last_sync", v).apply()

    /** Cars per row in the grids (1, 2 or 3), the same everywhere. */
    val columns by lazy { androidx.compose.runtime.mutableIntStateOf(sp.getInt("columns", 2).coerceIn(1, 3)) }
    fun setColumns(n: Int) { columns.intValue = n; sp.edit().putInt("columns", n).apply() }

    /** Garages created by me (also the empty ones). */
    var garages: List<String>
        get() = (sp.getString("garages", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("garages", v.joinToString("\n")).apply()

    /** Last version whose "what's new" was shown. */
    var seenVersion: Int
        get() = sp.getInt("seen_version", 0)
        set(v) = sp.edit().putInt("seen_version", v).apply()
}
