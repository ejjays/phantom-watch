package dev.phantomwatch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.phantomwatch.R

// shown when Download tapped but phantom downloader missing
@Composable
fun PhantomInstallDialog(onInstall: () -> Unit, onDismiss: () -> Unit) {
    val card = RoundedCornerShape(28.dp)
    val logo = 76.dp
    val overhang = logo / 2
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(minOf(maxWidth * 0.88f, 360.dp)).padding(top = overhang)) {
                Column(
                    Modifier.fillMaxWidth().clip(card)
                        .background(Color(0xFF262626))
                        .padding(start = 22.dp, end = 22.dp, top = 46.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.phantom_install_title), color = Color.White,
                        fontFamily = Lilita, fontSize = 26.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.phantom_install_subtitle), color = Color.White.copy(alpha = 0.76f),
                        fontFamily = WorkSans, fontSize = 13.sp, lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.11f)))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.phantom_install_sites), color = Color.White.copy(alpha = 0.42f),
                        fontFamily = WorkSans, fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SiteIcon(R.drawable.phantom_logo)
                        SiteIcon(R.drawable.ic_platform_youtube)
                        SiteIcon(R.drawable.ic_platform_facebook)
                        SiteIcon(R.drawable.ic_platform_instagram)
                        SiteIcon(R.drawable.ic_platform_tiktok)
                        SiteIcon(R.drawable.ic_platform_spotify)
                        MoreChip(stringResource(R.string.phantom_install_more))
                    }
                    Spacer(Modifier.height(24.dp))
                    Box(
                        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
                            .background(Cyan).pressClick(onInstall),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.phantom_install_cta), color = Color.Black,
                            fontFamily = WorkSans, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.pressClick(onDismiss).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            stringResource(R.string.phantom_install_later), color = Color.White.copy(alpha = 0.72f),
                            fontFamily = WorkSans, fontSize = 13.sp,
                        )
                    }
                }
                Image(
                    painterResource(R.drawable.ic_phantom_ghost), contentDescription = null,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = -overhang).requiredSize(logo),
                )
            }
        }
    }
}

@Composable
private fun SiteIcon(res: Int) {
    // no tint, brand colours stay as shipped
    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(res), contentDescription = null, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun MoreChip(text: String) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(Cyan.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = Cyan, fontFamily = WorkSans,
            fontWeight = FontWeight.Bold, fontSize = 9.sp,
        )
    }
}
