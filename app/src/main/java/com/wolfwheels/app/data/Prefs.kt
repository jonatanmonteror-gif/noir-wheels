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

    /** Last version whose "what's new" was shown. */
    var seenVersion: Int
        get() = sp.getInt("seen_version", 0)
        set(v) = sp.edit().putInt("seen_version", v).apply()
}
