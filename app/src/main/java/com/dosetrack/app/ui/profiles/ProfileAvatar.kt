package com.dosetrack.app.ui.profiles

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dosetrack.app.data.db.ProfileEntity
import com.dosetrack.app.domain.ProfileRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun ProfileEntity.displayColor(): Color = Color(colorArgb ?: ProfileRules.defaultColor(id))

/** Photo if the profile has one, otherwise initials on the profile colour. Decorative: callers label it. */
@Composable
fun ProfileAvatar(
    profile: ProfileEntity,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val photo by produceState<ImageBitmap?>(initialValue = null, profile.photoPath) {
        value = profile.photoPath?.let { path ->
            withContext(Dispatchers.IO) {
                File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
            }
        }
    }
    AvatarFrame(photo = photo, color = profile.displayColor(), initials = ProfileRules.initials(profile.name), modifier, size)
}

/** Preview of a picked (not yet saved) image, falling back to [profile]'s avatar. */
@Composable
fun PendingPhotoAvatar(
    uri: Uri,
    profileName: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { decodeSampled(context, uri, maxSide = 512) }
    }
    AvatarFrame(photo = photo, color = color, initials = ProfileRules.initials(profileName), modifier, size)
}

/** Avatar standing for all profiles (Today's "Everyone" view). */
@Composable
fun EveryoneAvatar(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

@Composable
private fun AvatarFrame(
    photo: ImageBitmap?,
    color: Color,
    initials: String,
    modifier: Modifier,
    size: Dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp,
                maxLines = 1,
            )
        }
    }
}

private fun decodeSampled(context: Context, uri: Uri, maxSide: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?.asImageBitmap()
}
