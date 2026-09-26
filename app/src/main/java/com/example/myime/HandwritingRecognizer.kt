package com.example.myime

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import org.json.JSONArray

class HandwritingRecognizer(private val context: Context) {

    private var initialized = false
    private val charset: List<String> = loadCharset()

    init {
        initialized = nativeInit(context.assets)
    }

    private fun loadCharset(): List<String> {
        return try {
            val text = context.assets.open("handwritten/charset.json")
                .bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val list = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun recognize(bitmap: Bitmap, topK: Int = 5): List<String> {
        if (!initialized) return emptyList()
        val indices = nativeRecognize(bitmap, topK) ?: return emptyList()
        val result = ArrayList<String>(indices.size)
        for (idx in indices) {
            val ch = charset.getOrNull(idx)
            if (ch != null) result.add(ch)
        }
        return result
    }

    private external fun nativeInit(assets: AssetManager): Boolean
    private external fun nativeRecognize(bitmap: Bitmap, topK: Int): IntArray?

    companion object {
        init {
            System.loadLibrary("handwriting")
        }
    }
}