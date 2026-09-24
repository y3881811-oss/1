package com.example.myime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.TextView

class MyInputMethodService : InputMethodService() {
    override fun onCreateInputView(): View {
        return TextView(this).apply {
            text = "Hello IME"
            textSize = 32f
        }
    }
}