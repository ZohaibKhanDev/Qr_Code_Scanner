package com.dev.qrcodescanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import com.github.alexzhirkevich.customqrgenerator.QrData
import com.github.alexzhirkevich.customqrgenerator.QrErrorCorrectionLevel
import com.github.alexzhirkevich.customqrgenerator.vector.QrCodeDrawable
import com.github.alexzhirkevich.customqrgenerator.vector.createQrVectorOptions
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorBallShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorColor
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorFrameShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorLogoPadding
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorLogoShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorPixelShape
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.Code128Writer
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QrStyle(
    val shape: String = "Square",
    val useGradient: Boolean = false,
    val useAppLogo: Boolean = false,
    val foreground: Int = AndroidColor.BLACK,
    val background: Int = AndroidColor.WHITE
)

val QrShapeStyles = listOf("Square", "Soft", "Round")

object QrEngine {

    private val gradientEndColor = AndroidColor.rgb(233, 30, 99)

    private fun pixelShapeFor(style: String): QrVectorPixelShape = when (style) {
        "Soft" -> QrVectorPixelShape.RoundCorners(.25f)
        "Round" -> QrVectorPixelShape.RoundCorners(.5f)
        else -> QrVectorPixelShape.Default
    }

    private fun ballShapeFor(style: String): QrVectorBallShape = when (style) {
        "Soft" -> QrVectorBallShape.RoundCorners(.25f)
        "Round" -> QrVectorBallShape.RoundCorners(.5f)
        else -> QrVectorBallShape.Default
    }

    private fun frameShapeFor(style: String): QrVectorFrameShape = when (style) {
        "Soft" -> QrVectorFrameShape.RoundCorners(.25f)
        "Round" -> QrVectorFrameShape.RoundCorners(.5f)
        else -> QrVectorFrameShape.Default
    }

    suspend fun renderQr(
        context: Context,
        content: String,
        style: QrStyle,
        logoBitmap: Bitmap? = null,
        outputSize: Int = 1024
    ): Bitmap = withContext(Dispatchers.Default) {
        val logoDrawable: Drawable? = logoBitmap?.toDrawable(context.resources)
            ?: if (style.useAppLogo) {
                ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            } else {
                null
            }

        val options = createQrVectorOptions {
            padding = .125f
            errorCorrectionLevel = QrErrorCorrectionLevel.High

            background {
                color = QrVectorColor.Solid(style.background)
            }

            if (logoDrawable != null) {
                logo {
                    drawable = logoDrawable
                    size = .2f
                    padding = QrVectorLogoPadding.Natural(.2f)
                    shape = QrVectorLogoShape.Circle
                }
            }

            colors {
                dark = QrVectorColor.Solid(style.foreground)
                ball = QrVectorColor.Solid(style.foreground)
                frame = if (style.useGradient) {
                    QrVectorColor.LinearGradient(
                        colors = listOf(
                            0f to style.foreground,
                            1f to gradientEndColor
                        ),
                        orientation = QrVectorColor
                            .LinearGradient
                            .Orientation
                            .LeftDiagonal
                    )
                } else {
                    QrVectorColor.Solid(style.foreground)
                }
            }

            shapes {
                darkPixel = pixelShapeFor(style.shape)
                ball = ballShapeFor(style.shape)
                frame = frameShapeFor(style.shape)
            }
        }

        QrCodeDrawable(QrData.Text(content), options).toBitmap(outputSize, outputSize)
    }

    suspend fun renderCode128(
        content: String,
        width: Int = 1000,
        height: Int = 360
    ): Bitmap = withContext(Dispatchers.Default) {
        val matrix = Code128Writer().encode(
            content,
            BarcodeFormat.CODE_128,
            width,
            height
        )
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[y * matrix.width + x] =
                    if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        Bitmap.createBitmap(
            pixels,
            matrix.width,
            matrix.height,
            Bitmap.Config.ARGB_8888
        )
    }

    private fun libraryDirectory(context: Context): File =
        File(context.filesDir, "codes").apply { mkdirs() }

    private fun writePng(file: File, bitmap: Bitmap): File {
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return file
    }

    suspend fun saveToLibrary(context: Context, bitmap: Bitmap): File =
        withContext(Dispatchers.IO) {
            writePng(
                File(libraryDirectory(context), "code_${System.currentTimeMillis()}.png"),
                bitmap
            )
        }

    suspend fun loadLibrary(context: Context): List<File> =
        withContext(Dispatchers.IO) {
            libraryDirectory(context)
                .listFiles { file -> file.extension == "png" }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
        }

    suspend fun deleteFromLibrary(file: File): Boolean =
        withContext(Dispatchers.IO) { file.delete() }

    suspend fun shareBitmap(context: Context, bitmap: Bitmap) {
        val file = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "shared").apply { mkdirs() }
            writePng(File(directory, "code_share.png"), bitmap)
        }
        shareFile(context, file)
    }

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Share code")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun createCameraUri(context: Context): Uri {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File.createTempFile("capture_", ".jpg", directory)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun decodeSquareBitmap(
        context: Context,
        uri: Uri,
        maxSize: Int = 768
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val limit = maxSize * 2
            val decoded: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val largest = max(info.size.width, info.size.height)
                    if (largest > limit) {
                        val scale = limit.toFloat() / largest
                        decoder.setTargetSize(
                            (info.size.width * scale).roundToInt(),
                            (info.size.height * scale).roundToInt()
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > limit) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }
            decoded?.let { cropToSquare(it, maxSize) }
        }.getOrNull()
    }

    private fun cropToSquare(source: Bitmap, maxSize: Int): Bitmap {
        val side = min(source.width, source.height)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        val square = Bitmap.createBitmap(source, x, y, side, side)
        return if (side > maxSize) {
            Bitmap.createScaledBitmap(square, maxSize, maxSize, true)
        } else {
            square
        }
    }

    suspend fun decodeThumbnail(file: File, maxSize: Int = 512): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > maxSize) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(file.absolutePath, options)
            }.getOrNull()
        }

    fun queryFileInfo(context: Context, uri: Uri): Pair<String, Long?> {
        var name = "document.pdf"
        var size: Long? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    cursor.getString(nameIndex)?.let { name = it }
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return name to size
    }
}