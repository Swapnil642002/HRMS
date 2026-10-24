package com.daksha.hrms.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream

object PdfHelper {

    fun saveAndOpenPdf(context: Context, responseBody: ResponseBody, filename: String): Result<Boolean> {
        return try {
            val cacheDir = context.cacheDir
            val pdfFile = File(cacheDir, filename)
            val inputStream = responseBody.byteStream()
            val outputStream = FileOutputStream(pdfFile)

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Open PDF with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
