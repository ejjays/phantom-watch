package dev.phantomwatch

object MkvSubs {
    data class Track(val codec: String, val cues: List<VidLove.Cue>)

    internal data class Raw(val start: Long, val dur: Long, val bytes: ByteArray)
    private data class Elem(val id: Long, val data: Long, val end: Long)

    fun extract(path: String): List<Track> {
        val out = mutableListOf<Track>()
        try {
            java.io.RandomAccessFile(path, "r").use { f ->
                val len = f.length()
                if (len < 16) return out
                var segData = -1L
                var segEnd = len
                var p = 0L
                var guard = 0
                while (p < len && guard++ < 32) {
                    val e = readElem(f, p, len) ?: break
                    if (e.id == 0x18538067L) {
                        segData = e.data
                        segEnd = e.end
                        break
                    }
                    if (e.end <= p) break
                    p = e.end
                }
                if (segData < 0) return out
                var scale = 1000000L
                val codecs = mutableMapOf<Long, String>()
                val bufs = mutableMapOf<Long, MutableList<Raw>>()
                p = segData
                guard = 0
                while (p < segEnd && guard++ < 1000000) {
                    val e = readElem(f, p, segEnd)
                    if (e == null) {
                        val r = resync(f, p + 1, segEnd) ?: break
                        p = r
                        continue
                    }
                    when (e.id) {
                        0x1549A966L -> {
                            var q = e.data
                            var g2 = 0
                            while (q < e.end && g2++ < 64) {
                                val c = readElem(f, q, e.end) ?: break
                                if (c.id == 0x2AD7B1L) scale = readUInt(f, c.data, (c.end - c.data).toInt().coerceAtMost(8))
                                if (c.end <= q) break
                                q = c.end
                            }
                        }
                        0x1654AE6BL -> {
                            var q = e.data
                            var g2 = 0
                            while (q < e.end && g2++ < 256) {
                                val t = readElem(f, q, e.end) ?: break
                                if (t.id == 0xAEL) {
                                    var num = -1L
                                    var type = -1L
                                    var codec = ""
                                    var r = t.data
                                    var g3 = 0
                                    while (r < t.end && g3++ < 64) {
                                        val c = readElem(f, r, t.end) ?: break
                                        val n = (c.end - c.data).toInt()
                                        when (c.id) {
                                            0xD7L -> num = readUInt(f, c.data, n.coerceAtMost(8))
                                            0x83L -> type = if (n > 0) readUInt(f, c.data, 1) else -1
                                            0x86L -> codec = readString(f, c.data, n.coerceAtMost(64))
                                        }
                                        if (c.end <= r) break
                                        r = c.end
                                    }
                                    if (num >= 0 && type == 0x11L &&
                                        (codec.startsWith("S_TEXT/ASS") || codec.startsWith("S_TEXT/SSA") || codec.startsWith("S_TEXT/UTF8"))
                                    ) {
                                        codecs[num] = codec
                                        bufs[num] = mutableListOf()
                                    }
                                }
                                if (t.end <= q) break
                                q = t.end
                            }
                        }
                        0x1F43B675L -> {
                            var tc = 0L
                            var q = e.data
                            var g2 = 0
                            while (q < e.end && g2++ < 100000) {
                                val c = readElem(f, q, e.end) ?: break
                                when (c.id) {
                                    0xE7L -> tc = readUInt(f, c.data, (c.end - c.data).toInt().coerceAtMost(8))
                                    0xA0L -> {
                                        var bPos = -1L
                                        var bEnd = -1L
                                        var dur = -1L
                                        var r = c.data
                                        var g3 = 0
                                        while (r < c.end && g3++ < 16) {
                                            val b = readElem(f, r, c.end) ?: break
                                            when (b.id) {
                                                0xA1L -> {
                                                    bPos = b.data
                                                    bEnd = b.end
                                                }
                                                0x9BL -> dur = readUInt(f, b.data, (b.end - b.data).toInt().coerceAtMost(8)) * scale / 1000000
                                            }
                                            if (b.end <= r) break
                                            r = b.end
                                        }
                                        if (bPos >= 0) readBlock(f, bPos, bEnd, tc, scale, dur, codecs, bufs)
                                    }
                                    0xA3L -> readBlock(f, c.data, c.end, tc, scale, -1L, codecs, bufs)
                                }
                                if (c.end <= q) break
                                q = c.end
                            }
                        }
                    }
                    if (e.end <= p) break
                    p = e.end
                }
                for ((num, codec) in codecs) {
                    val raws = bufs[num] ?: continue
                    val cues = if (codec.startsWith("S_TEXT/UTF8")) srtCues(raws) else assCues(raws)
                    AppLog.d("mkv subs track=$num codec=$codec cues=${cues.size}")
                    if (cues.isNotEmpty()) out.add(Track(codec, cues))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun readBlock(
        f: java.io.RandomAccessFile, pos: Long, end: Long,
        clusterTc: Long, scale: Long, dur: Long,
        codecs: Map<Long, String>, bufs: MutableMap<Long, MutableList<Raw>>,
    ) {
        try {
            val tn = readVint(f, pos, end) ?: return
            if (tn.first !in codecs) return
            var q = tn.second
            if (q + 3 > end) return
            f.seek(q)
            val hi = f.read()
            val lo = f.read()
            val flags = f.read()
            if (hi < 0 || lo < 0 || flags < 0) return
            val rel = ((hi shl 8) or lo).toShort().toLong()
            q += 3
            val absMs = maxOf(0L, (clusterTc + rel) * scale / 1000000)
            val buf = bufs[tn.first] ?: return
            when ((flags shr 1) and 0x3) {
                0 -> {
                    val n = (end - q).toInt()
                    if (n <= 0) return
                    val bytes = ByteArray(n)
                    f.seek(q)
                    f.readFully(bytes)
                    buf.add(Raw(absMs, dur, bytes))
                }
                1 -> {
                    f.seek(q)
                    var frames = f.read() + 1
                    q += 1
                    if (frames <= 0) return
                    while (--frames > 0) {
                        var size = 0
                        while (true) {
                            if (q >= end) return
                            f.seek(q++)
                            val b = f.read()
                            if (b < 0) return
                            size += b
                            if (b != 0xFF) break
                        }
                        if (size <= 0 || q + size > end) return
                        val bytes = ByteArray(size)
                        f.seek(q)
                        f.readFully(bytes)
                        q += size
                        buf.add(Raw(absMs, dur, bytes))
                    }
                    val n = (end - q).toInt()
                    if (n > 0) {
                        val bytes = ByteArray(n)
                        f.seek(q)
                        f.readFully(bytes)
                        buf.add(Raw(absMs, dur, bytes))
                    }
                }
                2 -> {
                    f.seek(q)
                    var frames = f.read() + 1
                    q += 1
                    if (frames <= 0) return
                    val rest = (end - q).toInt()
                    if (rest <= 0) return
                    val size = rest / frames
                    if (size <= 0) return
                    while (frames-- > 0) {
                        val bytes = ByteArray(size)
                        f.seek(q)
                        f.readFully(bytes)
                        q += size
                        buf.add(Raw(absMs, dur, bytes))
                    }
                }
                else -> return
            }
        } catch (_: Exception) {
        }
    }

    private fun assStamp(ms: Long): String {
        val v = maxOf(0L, ms)
        return "%d:%02d:%02d.%02d".format(v / 3600000, (v / 60000) % 60, (v / 1000) % 60, (v % 1000) / 10)
    }

    internal fun assCues(raws: List<Raw>): List<VidLove.Cue> {
        val sb = StringBuilder()
        for (r in raws.sortedBy { it.start }) {
            val text = String(r.bytes, Charsets.UTF_8)
            val parts = text.split(',', limit = 8)
            val fields = when {
                parts.size >= 8 -> listOf(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6], parts[7])
                parts.size == 7 -> listOf("0", parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6])
                else -> continue
            }
            val end = if (r.dur > 0) r.start + r.dur else r.start + 4000
            sb.append("Dialogue: 0,${assStamp(r.start)},${assStamp(end)},${fields[1]},${fields[2]},${fields[3]},${fields[4]},${fields[5]},${fields[6]},${fields[7]}\n")
        }
        val parsed = VidLove.parseAss(sb.toString())
        return clamp(parsed)
    }

    internal fun srtCues(raws: List<Raw>): List<VidLove.Cue> {
        val cues = mutableListOf<VidLove.Cue>()
        for (r in raws.sortedBy { it.start }) {
            val body = String(r.bytes, Charsets.UTF_8)
                .replace(Regex("\\{[^}]*\\}"), "").replace("\r", "").trim()
            if (body.isEmpty()) continue
            val end = if (r.dur > 0) r.start + r.dur else r.start + 4000
            if (end <= r.start) continue
            cues.add(VidLove.Cue(r.start, end, body))
        }
        return clamp(cues.sortedBy { it.start })
    }

    internal fun clamp(cues: List<VidLove.Cue>): List<VidLove.Cue> {
        return cues.mapIndexed { i, c ->
            val next = cues.getOrNull(i + 1)?.start ?: (c.start + 4000)
            c.copy(end = minOf(c.end, next, c.start + 8000))
        }.filter { it.end > it.start && it.text.isNotEmpty() }
    }

    private fun resync(f: java.io.RandomAccessFile, from: Long, limit: Long): Long? {
        try {
            val end = minOf(limit, from + 8000000)
            val chunk = ByteArray(65539)
            var p = from
            while (p < end) {
                val n = minOf(chunk.size, (end - p).toInt())
                if (n < 4) return null
                f.seek(p)
                f.readFully(chunk, 0, n)
                for (i in 0..n - 4) {
                    if (chunk[i] == 0x1F.toByte() && chunk[i + 1] == 0x43.toByte() &&
                        chunk[i + 2] == 0xB6.toByte() && chunk[i + 3] == 0x75.toByte()
                    ) {
                        return p + i
                    }
                }
                if (n < chunk.size) return null
                p += n - 3
            }
            return null
        } catch (_: Exception) {
            return null
        }
    }

    private fun readElem(f: java.io.RandomAccessFile, pos: Long, limit: Long): Elem? {
        try {
            if (pos + 2 > limit) return null
            f.seek(pos)
            val b0 = f.read()
            if (b0 < 0) return null
            val il = idLen(b0)
            if (il < 0 || pos + il + 1 > limit) return null
            val idBytes = ByteArray(il)
            idBytes[0] = b0.toByte()
            if (il > 1) f.readFully(idBytes, 1, il - 1)
            var id = 0L
            for (b in idBytes) id = id shl 8 or (b.toLong() and 0xff)
            val s0 = f.read()
            if (s0 < 0) return null
            val sl = sizeLen(s0)
            if (sl < 0 || pos + il + sl > limit) return null
            val sBytes = ByteArray(sl)
            sBytes[0] = s0.toByte()
            if (sl > 1) f.readFully(sBytes, 1, sl - 1)
            var size = s0.toLong() and (0xFF ushr sl).toLong()
            for (i in 1 until sl) size = size shl 8 or (sBytes[i].toLong() and 0xff)
            val data = pos + il + sl
            val allOnes = (1L shl (7 * sl)) - 1
            val end = if (size == allOnes) limit else minOf(limit, data + size)
            if (end < data) return null
            return Elem(id, data, end)
        } catch (_: Exception) {
            return null
        }
    }

    private fun idLen(b: Int): Int {
        var mask = 0x80
        var len = 1
        while (len <= 4 && b and mask == 0) {
            mask = mask shr 1
            len++
        }
        return if (len > 4) -1 else len
    }

    private fun sizeLen(b: Int): Int {
        var mask = 0x80
        var len = 1
        while (len <= 8 && b and mask == 0) {
            mask = mask shr 1
            len++
        }
        return if (len > 8) -1 else len
    }

    private fun readVint(f: java.io.RandomAccessFile, pos: Long, limit: Long): Pair<Long, Long>? {
        try {
            if (pos + 1 > limit) return null
            f.seek(pos)
            val b0 = f.read()
            if (b0 < 0) return null
            val len = sizeLen(b0)
            if (len < 0 || pos + len > limit) return null
            var v = b0.toLong() and (0xFF ushr len).toLong()
            for (i in 1 until len) {
                val b = f.read()
                if (b < 0) return null
                v = v shl 8 or b.toLong()
            }
            return v to pos + len
        } catch (_: Exception) {
            return null
        }
    }

    private fun readUInt(f: java.io.RandomAccessFile, pos: Long, n: Int): Long {
        f.seek(pos)
        var v = 0L
        repeat(n.coerceAtMost(8)) {
            val b = f.read()
            if (b < 0) throw java.io.EOFException()
            v = v shl 8 or b.toLong()
        }
        return v
    }

    private fun readString(f: java.io.RandomAccessFile, pos: Long, n: Int): String {
        if (n <= 0) return ""
        val bytes = ByteArray(n)
        f.seek(pos)
        f.readFully(bytes)
        return String(bytes, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
    }
}
