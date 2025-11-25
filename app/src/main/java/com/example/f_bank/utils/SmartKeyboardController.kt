package com.example.f_bank.utils

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.EditText

/**
 * Устанавливает телефонную клавиатуру для поля ввода номера телефона.
 * Всегда показывает цифровую клавиатуру для ввода телефона.
 */
object SmartKeyboardController {
    fun attach(editText: EditText, formatter: PhoneNumberFormatter? = null) {
        val controller = InternalController(editText, formatter)
        controller.initialize()
    }

    private class InternalController(
        private val editText: EditText,
        private val formatter: PhoneNumberFormatter?
    ) : TextWatcher {

        private var externalFocusListener: View.OnFocusChangeListener? = null
        private var lastDigits: String = ""

        fun initialize() {
            externalFocusListener = editText.onFocusChangeListener
            editText.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    applyPhoneInputType()
                }
                externalFocusListener?.onFocusChange(v, hasFocus)
            }
            editText.addTextChangedListener(this)
            applyPhoneInputType()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            val digitsOnly = s?.replace(Regex("[^0-9]"), "") ?: ""
            lastDigits = digitsOnly
            applyPhoneInputType()
        }

        private fun applyPhoneInputType() {
            val type = if (lastDigits.startsWith("7") && lastDigits.isNotEmpty()) {
                formatter?.setFormattingEnabled(false)
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            } else {
                formatter?.setFormattingEnabled(true)
                InputType.TYPE_CLASS_PHONE
            }

            if (editText.inputType == type) return

            val selection = editText.selectionStart
            editText.setRawInputType(type)
            val newSelection = if (selection >= 0) selection.coerceAtMost(editText.text?.length ?: 0) else editText.text?.length ?: 0
            editText.setSelection(newSelection)
        }
    }
}

