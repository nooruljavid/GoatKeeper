package com.goatkeeper.app.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.goatkeeper.app.data.FarmRecord
import com.goatkeeper.app.data.Goat
import java.io.File
import java.io.FileOutputStream

fun generateInventoryCSV(context: Context, goats: List<Goat>): File? {
    val csv = StringBuilder()
    csv.append("ID,Name,Breed,DOB,Gender,Status,Dam ID,Sire ID,Color/Markings,Microchip ID\n")
    goats.forEach { g ->
        csv.append("${g.id},${g.name},${g.breed},${g.dateOfBirth},${g.gender},${g.status},${g.damId},${g.sireId},${g.colorMarkings},${g.microchipId}\n")
    }
    
    return try {
        val file = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "inventory_export_${System.currentTimeMillis()}.csv")
        FileOutputStream(file).use { it.write(csv.toString().toByteArray()) }
        file
    } catch (e: Exception) {
        null
    }
}

fun generateGoatPDF(context: Context, goat: Goat, records: List<FarmRecord>): File? {
    val pdfDocument = PdfDocument()
    val titlePaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 18f
    }
    val headerPaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 14f
    }
    val textPaint = Paint().apply {
        textSize = 12f
    }

    // Page 1: Goat Info & Records
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
    var page = pdfDocument.startPage(pageInfo)
    var canvas: Canvas = page.canvas
    var y = 40f

    canvas.drawText("Goat Record: ${goat.name} (${goat.id})", 40f, y, titlePaint)
    y += 30f

    canvas.drawText("Details:", 40f, y, headerPaint)
    y += 20f
    
    val details = listOf(
        "Breed: ${goat.breed}",
        "Gender: ${goat.gender}",
        "Born: ${formatDate(goat.dateOfBirth)}",
        "Status: ${goat.status}",
        "Dam: ${goat.damId}",
        "Sire: ${goat.sireId}",
        "Microchip: ${goat.microchipId}",
        "Color/Markings: ${goat.colorMarkings}"
    )

    details.forEach { detail ->
        canvas.drawText(detail, 40f, y, textPaint)
        y += 15f
    }

    y += 20f
    canvas.drawText("History / Records:", 40f, y, headerPaint)
    y += 25f

    records.sortedByDescending { it.date }.forEach { r ->
        if (y > 800f) {
            pdfDocument.finishPage(page)
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = 40f
        }
        
        canvas.drawText("${formatDate(r.date)} - ${r.type}: ${r.title}", 40f, y, textPaint.apply { isFakeBoldText = true })
        y += 15f
        if (r.details.isNotBlank()) {
            canvas.drawText("Details: ${r.details}", 50f, y, textPaint.apply { isFakeBoldText = false })
            y += 15f
        }
        if (r.dueDate.isNotBlank()) {
            canvas.drawText("Next Due: ${formatDate(r.dueDate)}", 50f, y, textPaint)
            y += 15f
        }
        y += 10f
    }

    pdfDocument.finishPage(page)

    return try {
        val file = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "goat_record_${goat.id}.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        file
    } catch (e: Exception) {
        pdfDocument.close()
        null
    }
}
