package com.example.f_bank.utils

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptGenerator {
    
    /**
     * Генерирует текстовую квитанцию о переводе
     */
    fun generateReceiptText(
        fromCard: String,
        toCard: String,
        amount: Double,
        description: String? = null,
        transactionId: String? = null
    ): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val date = dateFormat.format(Date())
        
        val format = java.text.NumberFormat.getNumberInstance(Locale.getDefault())
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0
        val formattedAmount = format.format(amount)
        
        val receipt = StringBuilder()
        receipt.appendLine("═══════════════════════════════════")
        receipt.appendLine("         КВИТАНЦИЯ О ПЕРЕВОДЕ")
        receipt.appendLine("═══════════════════════════════════")
        receipt.appendLine()
        receipt.appendLine("Дата и время: $date")
        receipt.appendLine()
        receipt.appendLine("С карты: $fromCard")
        receipt.appendLine("На карту: $toCard")
        receipt.appendLine()
        receipt.appendLine("Сумма перевода: $formattedAmount ₸")
        receipt.appendLine()
        
        if (!description.isNullOrEmpty()) {
            receipt.appendLine("Комментарий: $description")
            receipt.appendLine()
        }
        
        if (!transactionId.isNullOrEmpty()) {
            receipt.appendLine("ID транзакции: $transactionId")
            receipt.appendLine()
        }
        
        receipt.appendLine("Статус: Успешно выполнено")
        receipt.appendLine()
        receipt.appendLine("═══════════════════════════════════")
        receipt.appendLine("     F-BANK")
        receipt.appendLine("═══════════════════════════════════")
        
        return receipt.toString()
    }
    
    /**
     * Генерирует HTML квитанцию для сохранения
     */
    fun generateReceiptHTML(
        fromCard: String,
        toCard: String,
        amount: Double,
        description: String? = null,
        transactionId: String? = null
    ): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val date = dateFormat.format(Date())
        
        val format = java.text.NumberFormat.getNumberInstance(Locale.getDefault())
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0
        val formattedAmount = format.format(amount)
        
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body {
                    font-family: Arial, sans-serif;
                    padding: 20px;
                    max-width: 600px;
                    margin: 0 auto;
                }
                .header {
                    text-align: center;
                    border-bottom: 2px solid #000;
                    padding-bottom: 10px;
                    margin-bottom: 20px;
                }
                .detail-row {
                    display: flex;
                    justify-content: space-between;
                    padding: 8px 0;
                    border-bottom: 1px solid #eee;
                }
                .label {
                    font-weight: bold;
                    color: #666;
                }
                .value {
                    color: #000;
                }
                .amount {
                    font-size: 24px;
                    font-weight: bold;
                    color: #4CAF50;
                }
                .footer {
                    text-align: center;
                    margin-top: 30px;
                    padding-top: 20px;
                    border-top: 2px solid #000;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>КВИТАНЦИЯ О ПЕРЕВОДЕ</h1>
            </div>
            
            <div class="detail-row">
                <span class="label">Дата и время:</span>
                <span class="value">$date</span>
            </div>
            
            <div class="detail-row">
                <span class="label">С карты:</span>
                <span class="value">$fromCard</span>
            </div>
            
            <div class="detail-row">
                <span class="label">На карту:</span>
                <span class="value">$toCard</span>
            </div>
            
            <div class="detail-row">
                <span class="label">Сумма перевода:</span>
                <span class="value amount">$formattedAmount ₸</span>
            </div>
            
            ${if (!description.isNullOrEmpty()) """
            <div class="detail-row">
                <span class="label">Комментарий:</span>
                <span class="value">$description</span>
            </div>
            """ else ""}
            
            ${if (!transactionId.isNullOrEmpty()) """
            <div class="detail-row">
                <span class="label">ID транзакции:</span>
                <span class="value">$transactionId</span>
            </div>
            """ else ""}
            
            <div class="detail-row">
                <span class="label">Статус:</span>
                <span class="value" style="color: #4CAF50; font-weight: bold;">Успешно выполнено</span>
            </div>
            
            <div class="footer">
                <p><strong>F-BANK</strong></p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    /**
     * Генерирует имя файла для квитанции
     */
    fun generateReceiptFileName(transactionId: String? = null): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val id = transactionId?.take(8) ?: timestamp
        return "receipt_$id.txt"
    }
}

