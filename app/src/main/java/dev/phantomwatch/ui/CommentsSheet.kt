package dev.phantomwatch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import dev.phantomwatch.R
import dev.phantomwatch.data.Title
import dev.phantomwatch.social.Social
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SheetBg = Color(0xFF181717)
private val OptionBg = Color(0xFF202223)
private val BodyInk = Color(0xFFF2F4F7)
private val MetaInk = Color(0xFFB0B3B8)
private val LinkInk = Color(0xFF0064D1)
private val MentionInk = Color(0xFF5AA7FF)
private val SendInk = Color(0xFF0866FF)
private val ComposerBg = Color(0xFF3A3C3D)
private val DividerInk = Color(0xFF65686C)
private val HandleInk = Color(0xFF6F7276)

@Composable
internal fun CommentsSheet(media: Title, scrim: Boolean = true, onDismiss: () -> Unit) {
    val id = media.id.toIntOrNull() ?: 0
    if (id <= 0) return

    val scope = rememberCoroutineScope()
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { state.targetState = true }
    val density = LocalDensity.current
    val closePx = with(density) { 120.dp.toPx() }
    var dragY by remember { mutableFloatStateOf(0f) }
    var page by remember { mutableStateOf<Social.Page?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var replyParent by remember { mutableStateOf(0L) }
    var replyName by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var actionError by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var reportTarget by remember { mutableStateOf<Social.Comment?>(null) }
    var deleteTarget by remember { mutableStateOf<Social.Comment?>(null) }
    var actionsTarget by remember { mutableStateOf<Social.Comment?>(null) }
    var actionShown by remember { mutableStateOf(false) }
    var actionClosing by remember { mutableStateOf(false) }
    fun closeActions() {
        if (actionClosing) return
        actionClosing = true
        scope.launch {
            delay(180)
            actionsTarget = null
            actionShown = false
            actionClosing = false
        }
    }
    val actionScrim by animateFloatAsState(
        if (actionShown && !actionClosing) 0.45f else 0f, tween(180), label = "actionScrim",
    )
    val actionOut by animateFloatAsState(
        if (actionClosing) 0f else 1f, tween(140), label = "actionOut",
    )
    val actionSlide by animateFloatAsState(
        if (actionShown && !actionClosing) 0f else 260f, tween(180), label = "actionSlide",
    )
    var open by remember { mutableStateOf(emptySet<Long>()) }
    val focus = remember { FocusRequester() }

    BackHandler(enabled = state.currentState && !actionShown) { state.targetState = false }
    LaunchedEffect(id, attempt) {
        failed = false
        page = Social.comments(media.kind, id)
        failed = page == null
    }
    LaunchedEffect(state.isIdle, state.currentState) {
        if (state.isIdle && !state.currentState) onDismiss()
    }
    LaunchedEffect(actionError) {
        if (actionError != null) {
            delay(4000)
            actionError = null
        }
    }
    LaunchedEffect(actionsTarget) { if (actionsTarget != null) actionShown = true }

    val startReply: (Social.Comment) -> Unit = { c ->
        replyParent = if (c.parent == 0L) c.id else c.parent
        replyName = c.author
        val tag = "@${c.author}"
        if (!input.contains(tag)) {
            input = if (input.isBlank()) "$tag " else "$input $tag "
        }
        focus.requestFocus()
    }
    val submit = {
        val body = input.trim()
        if (body.isNotEmpty() && !sending) {
            sending = true
            error = null
            scope.launch {
                val err = Social.post(media.kind, id, body, replyParent)
                sending = false
                if (err == null) {
                    if (replyParent > 0) open = open + replyParent
                    input = ""
                    replyParent = 0L
                    replyName = null
                    page = Social.comments(media.kind, id)
                } else {
                    error = err
                }
            }
        }
        Unit
    }
    val cancelReply = {
        replyName?.let { input = input.replaceFirst("@$it ", "").trimStart() }
        replyParent = 0L
        replyName = null
        Unit
    }

    val eatScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity.Zero
        }
    }
    val drag = Modifier.draggable(
        state = rememberDraggableState { d -> dragY = (dragY + d).coerceAtLeast(0f) },
        orientation = Orientation.Vertical,
        onDragStopped = { v ->
            if (dragY > closePx || v > 900f) {
                scope.launch {
                    animate(dragY, closePx * 8, animationSpec = tween(180)) { y, _ -> dragY = y }
                    state.targetState = false
                }
            } else {
                scope.launch {
                    animate(dragY, 0f, animationSpec = spring(stiffness = 900f)) { y, _ -> dragY = y }
                }
            }
        },
    )

    val scrimAlpha by animateFloatAsState(if (state.targetState && scrim) 0.45f else 0f, tween(260), label = "scrim")
    if (scrimAlpha > 0f) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    state.targetState = false
                },
        )
    }
    AnimatedVisibility(
        visibleState = state,
        enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
        exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxHeight()
                .offset { IntOffset(0, dragY.roundToInt()) }
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(SheetBg)
                .nestedScroll(eatScroll),
        ) {
            Column(drag) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(40.dp, 4.dp).clip(CircleShape)
                            .background(HandleInk),
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val p = page
                when {
                    p == null && failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.comments_unavailable), color = Slate500, fontSize = 13.sp, fontFamily = WorkSans)
                            Text(
                                stringResource(R.string.comments_tap_retry), color = Cyan, fontSize = 12.sp, fontFamily = WorkSans,
                                modifier = Modifier.padding(top = 6.dp).clickable { attempt++ },
                            )
                        }
                    }
                    p == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Cyan, modifier = Modifier.size(20.dp))
                    }
                    p.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.comments_empty),
                            color = Slate500, fontSize = 13.sp, fontFamily = WorkSans,
                        )
                    }
                    else -> {
                        val roots = p.items.filter { it.parent == 0L }
                        val kids = p.items.filter { it.parent != 0L }
                            .groupBy { it.parent }
                            .mapValues { (_, v) -> v.sortedBy { it.at } }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                        ) {
                            items(roots, key = { it.id }) { root ->
                                RootThread(
                                    root = root,
                                    replies = kids[root.id].orEmpty(),
                                    open = root.id in open,
                                    onToggle = {
                                        open = if (root.id in open) open - root.id else open + root.id
                                    },
                                    onReply = startReply,
                                    onActions = { actionsTarget = it },
                                )
                            }
                        }
                    }
                }
            }
            deleteTarget?.let { comment ->
                AlertDialog(
                    onDismissRequest = { deleteTarget = null },
                    title = { Text(stringResource(R.string.comments_delete_title)) },
                    text = { Text(stringResource(R.string.comments_delete_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            deleteTarget = null
                            scope.launch {
                                if (Social.deleteComment(comment.id)) {
                                    open = open - comment.id
                                    Social.comments(media.kind, id)?.let { page = it }
                                    actionError = R.string.comments_deleted to true
                                } else {
                                    actionError = R.string.comments_delete_failed to false
                                }
                            }
                        }) { Text(stringResource(R.string.common_delete)) }
                    },
                    dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) } },
                )
            }
            reportTarget?.let { comment ->
                AlertDialog(
                    onDismissRequest = { reportTarget = null },
                    title = { Text(stringResource(R.string.comments_report_title)) },
                    text = { Text(stringResource(R.string.comments_report_body)) },
                    confirmButton = {
                        Column {
                            Social.ReportReason.entries.forEach { reason ->
                                TextButton(onClick = {
                                    reportTarget = null
                                    scope.launch {
                                        val ok = Social.reportComment(comment.id, reason)
                                        actionError = if (ok) R.string.comments_reported to true else R.string.comments_report_failed to false
                                    }
                                }) { Text(reason.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            }
                        }
                    },
                    dismissButton = { TextButton(onClick = { reportTarget = null }) { Text(stringResource(R.string.common_cancel)) } },
                )
            }
            actionError?.let { (message, ok) ->
                Text(
                    stringResource(message),
                    color = if (ok) Cyan else Color(0xFFF87171),
                    fontSize = 12.sp,
                    fontFamily = WorkSans,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Composer(
                input = input,
                onInput = { input = it.take(500) },
                sending = sending,
                error = error?.let { stringResource(it) },
                replyTo = replyName,
                onCancelReply = cancelReply,
                focus = focus,
                onSubmit = submit,
            )
        }
        if (actionsTarget != null) {
            Dialog(
                onDismissRequest = ::closeActions,
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = actionScrim))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                closeActions()
                            },
                    )
                    Box(Modifier.fillMaxSize()) {
                        val context = LocalContext.current
                        actionsTarget?.let { target ->
                            Column(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().widthIn(max = 600.dp)
                                    .offset(y = actionSlide.dp)
                                    .graphicsLayer { alpha = actionOut }
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(OptionBg)
                                    .windowInsetsPadding(WindowInsets.navigationBars),
                            ) {
                                ReactionRow()
                                ActionRow(R.drawable.ic_reply, stringResource(R.string.comments_reply)) {
                                    closeActions()
                                    startReply(target)
                                }
                                ActionRow(R.drawable.ic_copy, stringResource(R.string.comments_copy)) {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("comment", target.body))
                                    closeActions()
                                }
                                if (target.mine) {
                                    ActionRow(R.drawable.ic_trash, stringResource(R.string.common_delete)) {
                                        closeActions()
                                        deleteTarget = target
                                    }
                                } else {
                                    ActionRow(R.drawable.ic_flag, stringResource(R.string.comments_report)) {
                                        closeActions()
                                        reportTarget = target
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ReactionRow() {
    Row(
        Modifier.fillMaxWidth().padding(start = 25.dp, top = 14.dp, end = 25.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("a110.json", "a189.json", "a139.json", "a006.json", "a071.json", "a045.json", "a049.json").forEach { file ->
            ReactionArt(file)
        }
    }
    Text(
        stringResource(R.string.comments_react), color = MetaInk, fontSize = 16.sp,
        fontFamily = WorkSans, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
    )
}

@Composable
private fun ReactionArt(file: String) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("animated/$file"))
    val anim by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        isPlaying = true,
        restartOnPlay = true,
    )
    val scale = when (file) {
        "a110.json" -> 0.88f
        "a189.json" -> 0.89f
        else -> 1.02f
    }
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        LottieAnimation(
            composition = composition,
            progress = { anim },
            modifier = Modifier.size(36.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

@Composable
private fun ActionRow(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val pop by animateFloatAsState(
            if (shown) 1f else 0.82f,
            tween(180, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
            label = "actionIcon",
        )
        Icon(
            painter = painterResource(icon), contentDescription = null, tint = BodyInk,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop },
        )
        Spacer(Modifier.width(12.dp))
        Text(label, color = BodyInk, fontSize = 16.sp, fontFamily = WorkSans)
    }
}

@Composable
private fun RootThread(
    root: Social.Comment,
    replies: List<Social.Comment>,
    open: Boolean,
    onToggle: () -> Unit,
    onReply: (Social.Comment) -> Unit,
    onActions: (Social.Comment) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        RootRow(root, onReply, onActions)
        if (replies.isNotEmpty()) {
            if (open) {
                replies.forEach { r -> ReplyRow(r, onReply, onActions) }
                ToggleRow(stringResource(R.string.comments_hide_replies), onToggle)
            } else {
                val n = replies.size
                ToggleRow(pluralStringResource(R.plurals.comments_show_replies, n, n), onToggle)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RootRow(
    c: Social.Comment,
    onReply: (Social.Comment) -> Unit,
    onActions: (Social.Comment) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
            .pointerInput(c.id) { detectTapGestures(onLongPress = { onActions(c) }) },
    ) {
        CommentAvatar(c.author, 40.dp)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            NameRow(c, 14, 12)
            Spacer(Modifier.height(4.dp))
            CommentBody(c.body, 15, 22, Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.comments_reply), color = MetaInk, fontSize = 13.sp, fontFamily = WorkSans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onReply(c) }
                    .padding(horizontal = 2.dp).heightIn(min = 32.dp).offset(x = (-4).dp),
            )
        }
    }
}

@Composable
private fun ReplyRow(
    c: Social.Comment,
    onReply: (Social.Comment) -> Unit,
    onActions: (Social.Comment) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 48.dp, top = 8.dp)
            .pointerInput(c.id) { detectTapGestures(onLongPress = { onActions(c) }) },
    ) {
        CommentAvatar(c.author, 24.dp)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            NameRow(c, 14, 12)
            Spacer(Modifier.height(3.dp))
            CommentBody(c.body, 14, 20, Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(1.dp))
            Text(
                stringResource(R.string.comments_reply), color = MetaInk, fontSize = 13.sp, fontFamily = WorkSans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onReply(c) }
                    .padding(horizontal = 2.dp).heightIn(min = 28.dp).offset(x = (-4).dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 40.dp).heightIn(min = 26.dp)
            .clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.rotate(90f)) { ChevronRightIcon(null, MetaInk, 14.dp) }
        Spacer(Modifier.width(4.dp))
        Text(text, color = MetaInk, fontSize = 12.sp, fontFamily = WorkSans, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NameRow(c: Social.Comment, nameSize: Int, metaSize: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "@${c.author}", color = BodyInk, fontSize = nameSize.sp, fontFamily = WorkSans,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        val ago = Social.ago(c.at)
        if (ago.isNotEmpty()) {
            Text(
                ago, color = MetaInk, fontSize = metaSize.sp, fontFamily = WorkSans,
                modifier = Modifier.padding(start = 13.dp),
            )
        }
    }
}

@Composable
private fun CommentBody(text: String, size: Int, line: Int, modifier: Modifier = Modifier) {
    val style = TextStyle(color = BodyInk, fontSize = size.sp, lineHeight = line.sp, fontFamily = WorkSans)
    val match = remember(text) { Regex("^(@\\S+)(\\s+)([\\s\\S]+)$").find(text) }
    if (match == null) {
        Text(text, style = style, modifier = modifier)
    } else {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = LinkInk)) {
                    append(match.groupValues[1])
                }
                append(match.groupValues[2])
                append(match.groupValues[3])
            },
            style = style,
            modifier = modifier,
        )
    }
}

@Composable
private fun Composer(
    input: String,
    onInput: (String) -> Unit,
    sending: Boolean,
    error: String?,
    replyTo: String?,
    onCancelReply: () -> Unit,
    focus: FocusRequester,
    onSubmit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerInk))
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
            replyTo?.let { name ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.comments_replying_to, name), color = MetaInk, fontSize = 13.sp,
                        fontFamily = WorkSans,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.common_cancel), color = LinkInk, fontSize = 13.sp,
                        fontFamily = WorkSans, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onCancelReply).padding(horizontal = 4.dp),
                    )
                }
            }
            error?.let {
                Text(
                    it, color = Color(0xFFF87171), fontSize = 12.sp, fontFamily = WorkSans,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(CircleShape)
                    .background(ComposerBg)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    textStyle = TextStyle(color = BodyInk, fontSize = 15.sp, fontFamily = WorkSans),
                    cursorBrush = SolidColor(MentionInk),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                    decorationBox = { field ->
                        Box {
                            if (input.isEmpty()) {
                                Text(
                                    stringResource(R.string.comments_hint), color = MetaInk, fontSize = 15.sp,
                                    fontFamily = WorkSans,
                                )
                            }
                            field()
                        }
                    },
                )
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .clickable(enabled = input.isNotBlank() && !sending) { onSubmit() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (input.isNotBlank()) {
                        SendIcon(contentDescription = stringResource(R.string.comments_post), tint = SendInk, size = 20.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentAvatar(handle: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            handle.trimStart('@').firstOrNull()?.uppercase() ?: "?",
            color = Slate300, fontSize = (size.value * 0.36f).sp, fontFamily = WorkSans,
            fontWeight = FontWeight.Medium,
        )
    }
}
