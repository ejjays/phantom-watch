package dev.phantomwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.R
import dev.phantomwatch.data.Title
import dev.phantomwatch.social.Social
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BodyInk = Slate200
private val LineInk = Color.White.copy(alpha = 0.12f)

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
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var actionError by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var reportTarget by remember { mutableStateOf<Social.Comment?>(null) }
    var deleteTarget by remember { mutableStateOf<Social.Comment?>(null) }
    var open by remember { mutableStateOf(emptySet<Long>()) }
    val focus = remember { FocusRequester() }

    BackHandler(enabled = state.currentState) { state.targetState = false }
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

    val startReply: (Social.Comment) -> Unit = { c ->
        replyParent = if (c.parent == 0L) c.id else c.parent
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
                    replyParent = 0
                    page = Social.comments(media.kind, id)
                } else {
                    error = err
                }
            }
        }
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxHeight()
                .offset { IntOffset(0, dragY.roundToInt()) }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Bg)
                .nestedScroll(eatScroll),
        ) {
            Column(drag) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(40.dp, 4.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f)),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.comments_title), color = Color.White, fontSize = 16.sp, fontFamily = Rubik,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    page?.let {
                        Text(stringResource(R.string.comments_count, it.total), color = Slate400, fontSize = 13.sp, fontFamily = Rubik)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .clickable { state.targetState = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        CloseIcon(contentDescription = stringResource(R.string.comments_close), tint = Slate300, size = 18.dp)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val p = page
                when {
                    p == null && failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.comments_unavailable), color = Slate500, fontSize = 13.sp, fontFamily = Rubik)
                            Text(
                                stringResource(R.string.comments_tap_retry), color = Cyan, fontSize = 12.sp, fontFamily = Rubik,
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
                            color = Slate500, fontSize = 13.sp, fontFamily = Rubik,
                        )
                    }
                    else -> {
                        val roots = p.items.filter { it.parent == 0L }
                        val kids = p.items.filter { it.parent != 0L }
                            .groupBy { it.parent }
                            .mapValues { (_, v) -> v.sortedBy { it.at } }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
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
                                    onDelete = { deleteTarget = it },
                                    onReport = { reportTarget = it },
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
                    fontFamily = Rubik,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Composer(
                input = input,
                onInput = { input = it.take(500) },
                sending = sending,
                error = error?.let { stringResource(it) },
                focus = focus,
                onSubmit = submit,
            )
        }
        }
    }
}

@Composable
private fun RootThread(
    root: Social.Comment,
    replies: List<Social.Comment>,
    open: Boolean,
    onToggle: () -> Unit,
    onReply: (Social.Comment) -> Unit,
    onDelete: (Social.Comment) -> Unit,
    onReport: (Social.Comment) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        RootRow(root, replies.isNotEmpty(), open, onReply, onDelete, onReport)
        if (replies.isNotEmpty()) {
            if (open) {
                replies.forEachIndexed { i, r ->
                    ReplyRow(r, i == replies.lastIndex, onReply, onDelete, onReport)
                }
                ToggleRow(stringResource(R.string.comments_hide_replies), Slate400, false, onToggle)
            } else {
                val n = replies.size
                ToggleRow(pluralStringResource(R.plurals.comments_show_replies, n, n), Cyan, true, onToggle)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RootRow(
    c: Social.Comment,
    hasReplies: Boolean,
    open: Boolean,
    onReply: (Social.Comment) -> Unit,
    onDelete: (Social.Comment) -> Unit,
    onReport: (Social.Comment) -> Unit,
) {
    val ring by animateFloatAsState(if (open) 1f else 0f, tween(220), label = "ring")
    Row(
        Modifier.fillMaxWidth().drawBehind {
            if (hasReplies) {
                val top = 58.dp.toPx()
                drawRect(LineInk, Offset(20.dp.toPx(), top), Size(1.dp.toPx(), size.height - top))
            }
        }.padding(top = 14.dp, bottom = 6.dp),
    ) {
        Box(
            Modifier.size(40.dp).drawBehind {
                if (ring > 0f) {
                    val r = size.minDimension / 2
                    drawCircle(Cyan.copy(alpha = 0.14f * ring), radius = r + 7.dp.toPx())
                    drawCircle(
                        Cyan.copy(alpha = ring), radius = r + 3.dp.toPx(),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            },
        ) {
            CommentAvatar(c.author, 40.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NameRow(c, 15, 13, onDelete, onReport)
            Spacer(Modifier.height(4.dp))
            CommentBody(c.body, 15, 22)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.comments_reply), color = Slate400, fontSize = 12.sp, fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onReply(c) }
                    .padding(horizontal = 2.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun ReplyRow(
    c: Social.Comment,
    last: Boolean,
    onReply: (Social.Comment) -> Unit,
    onDelete: (Social.Comment) -> Unit,
    onReport: (Social.Comment) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().drawBehind {
            val x = 20.dp.toPx()
            val w = 1.dp.toPx()
            val cx = x + w / 2
            val cy = 24.dp.toPx()
            val r = 12.dp.toPx()
            drawRect(LineInk, Offset(x, 0f), Size(w, if (last) cy - r else size.height))
            drawPath(
                Path().apply {
                    moveTo(cx, cy - r)
                    quadraticBezierTo(cx, cy, cx + r, cy)
                    lineTo(52.dp.toPx(), cy)
                },
                LineInk, style = Stroke(w, cap = StrokeCap.Round),
            )
        }.padding(start = 52.dp, top = 12.dp),
    ) {
        CommentAvatar(c.author, 24.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            NameRow(c, 14, 12, onDelete, onReport)
            Spacer(Modifier.height(3.dp))
            CommentBody(c.body, 14, 20)
            Spacer(Modifier.height(1.dp))
            Text(
                stringResource(R.string.comments_reply), color = Slate400, fontSize = 11.sp, fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onReply(c) }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(text: String, color: Color, line: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().drawBehind {
            if (!line) return@drawBehind
            val x = 20.dp.toPx()
            val w = 1.dp.toPx()
            val cx = x + w / 2
            val cy = size.height / 2f
            val r = 12.dp.toPx()
            drawRect(LineInk, Offset(x, 0f), Size(w, cy - r))
            drawPath(
                Path().apply {
                    moveTo(cx, cy - r)
                    quadraticBezierTo(cx, cy, cx + r, cy)
                    lineTo(52.dp.toPx(), cy)
                },
                LineInk, style = Stroke(w, cap = StrokeCap.Round),
            )
        }.padding(start = 52.dp).heightIn(min = 36.dp)
            .clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = color, fontSize = 12.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NameRow(
    c: Social.Comment,
    nameSize: Int,
    metaSize: Int,
    onDelete: (Social.Comment) -> Unit,
    onReport: (Social.Comment) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "@${c.author}", color = Color.White, fontSize = nameSize.sp, fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        val ago = Social.ago(c.at)
        if (ago.isNotEmpty()) {
            Text(
                ago, color = Slate500, fontSize = metaSize.sp, fontFamily = Rubik,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            if (c.mine) stringResource(R.string.common_delete) else stringResource(R.string.comments_report), color = Slate500, fontSize = 11.sp,
            fontFamily = Rubik,
            modifier = Modifier.padding(start = 10.dp).clickable {
                if (c.mine) onDelete(c) else onReport(c)
            },
        )
    }
}

@Composable
private fun CommentBody(text: String, size: Int, line: Int) {
    val style = TextStyle(color = BodyInk, fontSize = size.sp, lineHeight = line.sp, fontFamily = Rubik)
    val match = remember(text) { Regex("^(@\\S+)(\\s+)([\\s\\S]+)$").find(text) }
    val modifier = Modifier
    if (match == null) {
        Text(text, style = style, modifier = modifier)
    } else {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White)) {
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
    focus: FocusRequester,
    onSubmit: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
    ) {
        error?.let {
            Text(
                it, color = Color(0xFFF87171), fontSize = 12.sp, fontFamily = Rubik,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f).focusRequester(focus),
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = Rubik),
                cursorBrush = SolidColor(Cyan),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                decorationBox = { field ->
                    Box {
                        if (input.isEmpty()) {
                            Text(
                                stringResource(R.string.comments_hint), color = Slate500, fontSize = 15.sp,
                                fontFamily = Rubik,
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
                    SendIcon(contentDescription = stringResource(R.string.comments_post), tint = Cyan, size = 20.dp)
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
            color = Slate300, fontSize = (size.value * 0.36f).sp, fontFamily = Rubik,
            fontWeight = FontWeight.Medium,
        )
    }
}
