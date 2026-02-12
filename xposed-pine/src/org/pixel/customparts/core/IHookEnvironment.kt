package org.pixel.customparts.core

import android.content.Context





interface IHookEnvironment {
    





    fun isEnabled(context: Context?, key: String, default: Boolean = false): Boolean

    





    fun getInt(context: Context?, key: String, default: Int): Int

    






    fun getFloat(context: Context?, key: String, default: Float): Float

    


    fun log(tag: String, message: String)

    


    fun logError(tag: String, message: String, t: Throwable? = null)
}