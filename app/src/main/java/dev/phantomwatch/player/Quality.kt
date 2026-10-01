package dev.phantomwatch.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dev.phantomwatch.core.AppLog

// the rungs this stream really carries, so the picker can never offer a resolution the provider
// lacks; a single-rendition stream still counts, so the menu can report what is playing
internal fun videoGroupOf(tracks: Tracks?): Tracks.Group? =
    tracks?.groups?.filter { it.type == C.TRACK_TYPE_VIDEO }?.maxByOrNull { it.length }

// a cinemascope encode is 1920x800; users still call that 1080p, so the label comes from the long side
internal fun qualityLabel(w: Int, h: Int): String = when (maxOf(w, h)) {
    in 3840..Int.MAX_VALUE -> "2160p"
    in 2560 until 3840 -> "1440p"
    in 1920 until 2560 -> "1080p"
    in 1280 until 1920 -> "720p"
    in 854 until 1280 -> "480p"
    in 640 until 854 -> "360p"
    in 426 until 640 -> "240p"
    else -> "${h}p"
}

data class Rung(val height: Int, val label: String)

internal fun rungsOf(sizes: List<Pair<Int, Int>>): List<Rung> =
    sizes.filter { it.second > 0 }.sortedByDescending { it.second }
        .map { Rung(it.second, qualityLabel(it.first, it.second)) }
        .distinctBy { it.label }

// 0 means ABR is still free to switch, i.e. "Auto"
internal fun currentHeightOf(g: Tracks.Group?): Int {
    val sel = g?.let { grp ->
        (0 until grp.length).filter { grp.isTrackSelected(it) }.map { grp.getTrackFormat(it).height }.filter { it > 0 }.distinct()
    }.orEmpty()
    return if (sel.size == 1) sel[0] else 0
}

internal class QualityState(val group: Tracks.Group?, val rungs: List<Rung>, val current: Int)

internal fun qualityOf(tracks: Tracks?): QualityState {
    val g = videoGroupOf(tracks)
    val sizes = g?.let { grp -> (0 until grp.length).map { grp.getTrackFormat(it).let { f -> f.width to f.height } } }.orEmpty()
    return QualityState(g, rungsOf(sizes), currentHeightOf(g))
}

internal fun pickQuality(exo: ExoPlayer, g: Tracks.Group?, h: Int) {
    val sel = exo.trackSelector as? DefaultTrackSelector ?: return
    val p = sel.buildUponParameters()
    if (h <= 0) {
        sel.parameters = p.clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
        AppLog.d("quality auto")
        return
    }
    if (g == null) return
    val i = (0 until g.length).firstOrNull { g.getTrackFormat(it).height == h } ?: return
    sel.parameters = p.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, listOf(i))).build()
    AppLog.d("quality ${h}p")
}
