package dev.phantomwatch.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.res.*
import dev.phantomwatch.ui.PlatformBackHandler
import dev.phantomwatch.ui.WorkSans
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileScreen(
    name: String,
    subtitle: String?,
    picture: String?,
    anonymous: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onPickAvatar: () -> Unit,
    onSignIn: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    Box(
        Modifier.fillMaxSize().background(SettingsCanvas).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.fillMaxSize().widthIn(max = 600.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClose)
                Text(
                    stringResource(Res.string.you_profile_title), color = SettingsText,
                    fontFamily = WorkSans, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier.size(112.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPickAvatar,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (picture != null) {
                    AsyncImage(
                        model = picture, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().clip(CircleShape),
                    )
                } else {
                    Image(
                        painter = painterResource(
                            if (anonymous) Res.drawable.anonymous_avatar else Res.drawable.guest_avatar,
                        ),
                        contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(112.dp),
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(32.dp)
                        .clip(CircleShape).background(SettingsAccent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_pen), contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                name, color = SettingsText, fontFamily = WorkSans, fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(48.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                if (anonymous) SignInButton(busy, onSignIn)
                if (subtitle != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        subtitle, color = SettingsText2, fontFamily = WorkSans, fontSize = 13.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
