package com.maksimowiczm.foodyou.app.ui.food.pending

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.maksimowiczm.foodyou.food.infrastructure.PENDING_PRODUCT_PHOTO_DIRECTORY
import foodyou.app.generated.resources.*
import java.io.File
import java.util.UUID
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun PendingProductPhoto(photoPath: String, modifier: Modifier) {
    val context = LocalContext.current
    val file = remember(photoPath) {
        context.filesDir.resolve(PENDING_PRODUCT_PHOTO_DIRECTORY).resolve(photoPath)
    }
    val bitmap = remember(file.absolutePath) {
        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }

    Box(modifier = modifier) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            )
        }
    }
}

@Composable
internal actual fun TakeNutritionPhotoButton(
    onPhotoTaken: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            pendingFile = null
            if (success && file != null) {
                onPhotoTaken(file.name)
            } else {
                file?.delete()
            }
        }

    FilledTonalButton(
        onClick = {
            val directory = context.filesDir.resolve(PENDING_PRODUCT_PHOTO_DIRECTORY)
            directory.mkdirs()
            val file = directory.resolve("${UUID.randomUUID()}.jpg")
            pendingFile = file
            val uri: Uri =
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            launcher.launch(uri)
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp).size(18.dp),
        )
        Text(stringResource(Res.string.neutral_take_nutrition_photo))
    }
}
