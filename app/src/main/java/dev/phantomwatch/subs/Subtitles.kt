package dev.phantomwatch.subs

import android.net.Uri
import dev.phantomwatch.data.Sub
import java.io.File

data class SubtitleCue(var start: Long, var end: Long, val text: String)

object Subtitles {
    private val TIMING = Regex("""(?:(\d+):)?(\d+):(\d+)[,.](\d+)\s*-->\s*(?:(\d+):)?(\d+):(\d+)[,.](\d+)""")

    fun englishFirst(subs: List<Sub>): List<Sub> =
        subs.sortedBy { if (it.label.contains("english", true)) 0 else 1 }

    private fun ms(h: String, m: String, s: String, ms: String): Long =
        h.ifEmpty { "0" }.toLong() * 3600000 + m.toLong() * 60000 + s.toLong() * 1000 +
            ms.padEnd(3, '0').take(3).toLong()

    private fun decode(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    fun parseAss(text: String): List<SubtitleCue> {
        val out = mutableListOf<SubtitleCue>()
        val dlg = Regex("^Dialogue:\\s*[^,]*,([^,]+),([^,]+),[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,(.*)$", RegexOption.IGNORE_CASE)
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val m = dlg.find(raw.trim()) ?: continue
            val s = stampToMs(m.groupValues[1].trim())
            val e = stampToMs(m.groupValues[2].trim())
            if (s == null || e == null || e <= s) continue
            val body = decode(m.groupValues[3].replace(Regex("\\{[^}]*\\}"), "")
                .replace("\\N", "\n").replace("\\n", "\n").trim())
            if (body.isEmpty()) continue
            out.add(SubtitleCue(s, e, body))
        }
        return out.sortedBy { it.start }
    }

    private fun stampToMs(stamp: String): Long? {
        return try {
            val parts = stamp.trim().split(':')
            if (parts.size != 3) return null
            val h = parts[0].toLong()
            val m = parts[1].toLong()
            val sec = parts[2].replace(',', '.').toDouble()
            (h * 3600000 + m * 60000 + (sec * 1000)).toLong()
        } catch (_: Exception) {
            null
        }
    }

    fun parseCues(text: String): List<SubtitleCue> {
        val out = mutableListOf<SubtitleCue>()
        for (block in text.removePrefix("\uFEFF").split(Regex("""\r?\n\r?\n"""))) {
            val lines = block.split(Regex("""\r?\n""")).map { it.trim() }.filter { it.isNotEmpty() }
            val ti = lines.indexOfFirst { TIMING.containsMatchIn(it) }
            if (ti < 0) continue
            val m = TIMING.find(lines[ti]) ?: continue
            val body = decode(lines.drop(ti + 1).joinToString("\n")
                .replace(Regex("""<[^>]*>"""), "").trim())
            if (body.isEmpty()) continue
            val g = m.groupValues
            out.add(SubtitleCue(ms(g[1], g[2], g[3], g[4]), ms(g[5], g[6], g[7], g[8]), body))
        }
        return out
    }

    private fun stamp(t: Long): String {
        val v = maxOf(0, t)
        return "%02d:%02d:%02d.%03d".format(v / 3600000, (v / 60000) % 60, (v / 1000) % 60, v % 1000)
    }

    fun renderCues(cues: List<SubtitleCue>): String {
        val sb = StringBuilder("WEBVTT\n\n")
        for (c in cues) {
            if (c.end <= c.start) continue
            sb.append("${stamp(c.start)} --> ${stamp(c.end)}\n${c.text}\n\n")
        }
        return sb.toString()
    }

    fun cuesToFile(cues: List<SubtitleCue>, cacheDir: File): File? {
        return try {
            if (cues.isEmpty()) return null
            val f = File(cacheDir, "ai-en.vtt")
            f.writeText(renderCues(cues))
            f
        } catch (_: Exception) {
            null
        }
    }

    fun shiftSubFile(raw: String, offsetMs: Long, cacheDir: File): File? {
        return try {
            val shifted = parseCues(raw).map { it.copy(start = it.start + offsetMs, end = it.end + offsetMs) }
                .filter { it.end > 0 }
            if (shifted.isEmpty()) return null
            val sb = StringBuilder("WEBVTT\n\n")
            for (c in shifted) sb.append("${stamp(c.start)} --> ${stamp(c.end)}\n${c.text}\n\n")
            val f = File(cacheDir, "shifted.vtt")
            f.writeText(sb.toString())
            f
        } catch (_: Exception) {
            null
        }
    }

    fun mimeOf(url: String): String {
        val path = Uri.parse(url).path.orEmpty()
        return if (path.endsWith(".srt", true)) "application/x-subrip" else "text/vtt"
    }
}
