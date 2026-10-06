package com.example.photostorage.ui

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.photostorage.domain.PhotoRecord
import com.example.photostorage.ui.theme.Mint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GlassMetric(title: String, value: String, detail: String) {
    Card(
        modifier = Modifier.fillMaxWidth().glassBorder(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .09f)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text(title, color = Color(0xFFB8C4BC), fontSize = 13.sp)
            Text(value, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Mint)
            Text(detail, color = Color(0xFF98A59C), fontSize = 12.sp)
        }
    }
}

@Composable
internal fun PhotoPeekDialog(photo: PhotoRecord, onDismiss: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.id) {
        value = withContext(Dispatchers.IO) { resolver.fullPreview(photo) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .94f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = photo.displayName,
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    contentScale = ContentScale.Fit,
                )
            } ?: CircularProgressIndicator(color = Mint)
            Text(
                "Quick preview • tap anywhere to close",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
            )
        }
    }
}

@Composable
internal fun DetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFACB8AF), fontSize = 11.sp)
        }
    }
}

@Composable
internal fun PhotoThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { resolver.thumbnail(uri) }
    }
    Box(modifier.background(Color(0xFF28332C)), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: Icon(Icons.Default.Photo, null, tint = Color(0xFF91A097))
    }
}

private fun ContentResolver.thumbnail(uri: Uri) = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) loadThumbnail(uri, Size(360, 360), null)
    else openInputStream(uri)?.use(BitmapFactory::decodeStream)
}.getOrNull()

internal fun ContentResolver.fullPreview(photo: PhotoRecord) = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(this, photo.uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val sourceLongEdge = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            if (sourceLongEdge > 4096) {
                val scale = 4096f / sourceLongEdge
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    } else openInputStream(photo.uri)?.use(BitmapFactory::decodeStream)
}.getOrNull()

@Composable
internal fun TextButtonLike(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Mint,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
    )
}
