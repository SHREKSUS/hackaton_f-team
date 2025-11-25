package com.example.f_bank.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class PhoneNumberFormatter(private val editText: EditText) : TextWatcher {
    private var isFormatting = false
    private var deletingBackward = false
    private var previousText = ""
    private var formattingEnabled = true

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        if (!isFormatting) {
            deletingBackward = count > after
            previousText = s?.toString() ?: ""
        }
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (isFormatting || !formattingEnabled) return

        val text = s.toString()
        
        // Если текст содержит "@" или буквы, это email - не форматируем
        if (text.contains("@") || text.matches(Regex(".*[a-zA-Zа-яА-Я].*"))) {
            return
        }
        
        val digitsOnly = text.replace(Regex("[^0-9]"), "")
        
        // Если нет цифр, очищаем поле
        if (digitsOnly.isEmpty()) {
            if (text.isNotEmpty()) {
                isFormatting = true
                editText.setText("")
                editText.setSelection(0)
                isFormatting = false
            }
            return
        }

        // Если удаляем назад и удалили нецифровой символ, нужно удалить соответствующую цифру
        if (deletingBackward && previousText.length > text.length) {
            val previousDigits = previousText.replace(Regex("[^0-9]"), "").length
            val currentDigits = digitsOnly.length
            
            // Если количество цифр не изменилось, значит удалили нецифровой символ
            // Нужно удалить следующую цифру
            if (previousDigits == currentDigits && currentDigits > 0) {
                val digits = digitsOnly.toMutableList()
                val cursorPos = editText.selectionStart
                val digitsBeforeCursor = text.substring(0, cursorPos).replace(Regex("[^0-9]"), "").length
                
                // Удаляем цифру после курсора
                if (digitsBeforeCursor < digits.size) {
                    digits.removeAt(digitsBeforeCursor)
                }
                
                val newDigits = digits.joinToString("")
                if (newDigits.isEmpty()) {
                    isFormatting = true
                    editText.setText("")
                    editText.setSelection(0)
                    isFormatting = false
                    return
                }
                
                val formatted = formatDigits(newDigits)
                isFormatting = true
                editText.setText(formatted)
                
                // Позиционируем курсор после удаленной цифры
                var newCursorPos = 0
                var digitCount = 0
                for (i in formatted.indices) {
                    if (formatted[i].isDigit()) {
                        digitCount++
                    }
                    if (digitCount >= digitsBeforeCursor) {
                        newCursorPos = i + 1
                        break
                    }
                }
                editText.setSelection(newCursorPos.coerceAtMost(formatted.length))
                isFormatting = false
                return
            }
        }

        // Форматируем номер
        val formatted = formatDigits(digitsOnly)

        // Если отформатированный текст отличается от текущего
        if (formatted != text) {
            isFormatting = true
            
            val cursorPos = editText.selectionStart
            editText.setText(formatted)
            
            // Вычисляем новую позицию курсора
            val newCursorPos = calculateNewCursorPosition(
                previousText,
                text,
                formatted,
                cursorPos,
                deletingBackward
            )
            
            editText.setSelection(newCursorPos.coerceIn(0, formatted.length))
            isFormatting = false
        }
    }

    private fun calculateNewCursorPosition(
        previousText: String,
        currentText: String,
        newText: String,
        oldCursorPos: Int,
        isDeleting: Boolean
    ): Int {
        if (isDeleting) {
            // При удалении сохраняем позицию относительно цифр
            val digitsBeforeCursor = currentText.substring(0, oldCursorPos)
                .replace(Regex("[^0-9]"), "").length
            
            var digitCount = 0
            for (i in newText.indices) {
                if (newText[i].isDigit()) {
                    digitCount++
                }
                if (digitCount > digitsBeforeCursor) {
                    return i
                }
            }
            return newText.length
        } else {
            // При вводе курсор после последней введенной цифры
            val digitsCount = currentText.replace(Regex("[^0-9]"), "").length
            var digitCount = 0
            for (i in newText.indices) {
                if (newText[i].isDigit()) {
                    digitCount++
                }
                if (digitCount >= digitsCount) {
                    return i + 1
                }
            }
            return newText.length
        }
    }

    companion object {
        /**
         * Удаляет форматирование из номера телефона, оставляя только цифры
         * @param phone Отформатированный номер (например, 7(777)777-77-77)
         * @return Только цифры (например, 77777777777)
         */
        fun unformatPhone(phone: String): String {
            return phone.replace(Regex("[^0-9]"), "")
        }

        /**
         * Проверяет, является ли строка валидным телефоном в формате 7(XXX)XXX-XX-XX
         */
        fun isValidFormattedPhone(phone: String): Boolean {
            val digits = unformatPhone(phone)
            return digits.length == 11 && digits.startsWith("7")
        }

        fun formatDigits(phone: String): String {
            val digits = phone.replace(Regex("[^0-9]"), "")
            if (digits.isEmpty()) return ""

            val phoneDigits = if (digits.startsWith("7")) {
                digits.take(11)
            } else {
                "7${digits.take(10)}"
            }

            return buildString {
                if (phoneDigits.isNotEmpty()) {
                    append(phoneDigits[0])
                }

                if (phoneDigits.length > 1) {
                    append("(")
                    val areaCode = phoneDigits.substring(1, minOf(4, phoneDigits.length))
                    append(areaCode)
                    if (areaCode.length == 3) {
                        append(")")
                    }
                }

                if (phoneDigits.length > 4) {
                    val firstPart = phoneDigits.substring(4, minOf(7, phoneDigits.length))
                    append(firstPart)
                    if (firstPart.length == 3) {
                        append("-")
                    }
                }

                if (phoneDigits.length > 7) {
                    val secondPart = phoneDigits.substring(7, minOf(9, phoneDigits.length))
                    append(secondPart)
                    if (secondPart.length == 2) {
                        append("-")
                    }
                }

                if (phoneDigits.length > 9) {
                    append(phoneDigits.substring(9, minOf(11, phoneDigits.length)))
                }
            }
        }
    }

    fun setFormattingEnabled(enabled: Boolean) {
        if (formattingEnabled == enabled) return
        formattingEnabled = enabled
        val digitsOnly = editText.text?.replace(Regex("[^0-9]"), "") ?: ""

        isFormatting = true
        if (!formattingEnabled) {
            editText.setText(digitsOnly)
            editText.setSelection(digitsOnly.length)
        } else {
            val formatted = formatDigits(digitsOnly)
            editText.setText(formatted)
            editText.setSelection(formatted.length)
        }
        isFormatting = false
    }
}