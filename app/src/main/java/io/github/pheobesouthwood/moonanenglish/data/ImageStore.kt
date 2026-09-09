package io.github.pheobesouthwood.moonanenglish.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.pheobesouthwood.moonanenglish.domain.StoredImage
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ImageStore(private val context: Context) {
    private val root = File(context.filesDir, "images").apply { mkdirs() }

    fun newCameraUri(): Pair<Uri, File> {
        val file = File(root, "capture-${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file) to file
    }

    fun import(uri: Uri): StoredImage {
        val id = UUID.randomUUID().toString()
        val destination = File(root, "$id.jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取图片" }
            val bitmap = BitmapFactory.decodeStream(input) ?: error("不支持的图片格式")
            FileOutputStream(destination).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
        }
        return StoredImage(id, "images/${destination.name}")
    }

    fun fromCaptured(file: File): StoredImage {
        require(file.exists() && file.length() > 0) { "相机没有生成图片" }
        return StoredImage(file.nameWithoutExtension, "images/${file.name}")
    }

    fun resolve(image: StoredImage): File = File(context.filesDir, image.relativePath)

    fun prepareForUpload(image: StoredImage): ByteArray {
        val source = BitmapFactory.decodeFile(resolve(image).absolutePath) ?: error("图片已损坏")
        val scale = minOf(1f, 2048f / maxOf(source.width, source.height))
        val resized = if (scale < 1f) Bitmap.createScaledBitmap(
            source, (source.width * scale).toInt(), (source.height * scale).toInt(), true
        ) else source
        val matrix = Matrix().apply { postRotate(image.rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(resized, 0, 0, resized.width, resized.height, matrix, true)
        val insetX = rotated.width * image.cropInsetPercent / 100
        val insetY = rotated.height * image.cropInsetPercent / 100
        val cropped = if (insetX * 2 < rotated.width && insetY * 2 < rotated.height) {
            Bitmap.createBitmap(rotated, insetX, insetY, rotated.width - insetX * 2, rotated.height - insetY * 2)
        } else rotated
        return java.io.ByteArrayOutputStream().use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    fun delete(image: StoredImage) { resolve(image).delete() }
    fun clear() { root.listFiles()?.forEach(File::delete) }
}
