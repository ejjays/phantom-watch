package dev.phantomwatch.lan

import java.net.URI

// hls.js fetches every child URI itself, so each one has to point back at the phone
internal object LanPlaylist {
    private val URI_TAGS = setOf(
        "#EXT-X-KEY", "#EXT-X-SESSION-KEY", "#EXT-X-MAP", "#EXT-X-MEDIA",
        "#EXT-X-I-FRAME-STREAM-INF", "#EXT-X-SESSION-DATA", "#EXT-X-PART",
        "#EXT-X-PRELOAD-HINT", "#EXT-X-RENDITION-REPORT",
    )
    private val ATTR = Regex("""URI="([^"]*)"""")

    // base is the URL after redirects: a master that 302s to a CDN resolves relative
    // segments against the CDN, not against the provider's host
    fun rewrite(text: String, base: String, link: (String) -> String): String {
        val baseUri = runCatching { URI(base) }.getOrNull() ?: return text
        return text.split("\n").joinToString("\n") { raw ->
            val cr = raw.endsWith("\r")
            val line = if (cr) raw.dropLast(1) else raw
            val out = when {
                line.isBlank() -> line
                line.startsWith("#") -> attrs(line, baseUri, link)
                else -> linkOf(line, baseUri, link)
            }
            if (cr) out + "\r" else out
        }
    }

    private fun attrs(line: String, base: URI, link: (String) -> String): String {
        if (line.substringBefore(":").uppercase() !in URI_TAGS) return line
        return ATTR.replace(line) { m -> "URI=\"" + linkOf(m.groupValues[1], base, link) + "\"" }
    }

    // EXT-X-BYTERANGE offsets are relative to the segment, so rewriting the URI keeps them valid
    private fun linkOf(raw: String, base: URI, link: (String) -> String): String {
        if (raw.isBlank()) return raw
        val abs = runCatching { base.resolve(raw).toString() }.getOrNull() ?: return raw
        return link(abs)
    }
}
