package dev.phantomwatch.provider

object DeadReleases {
    val hashes = mutableSetOf<String>()
    val urls = mutableSetOf<String>()
}

object DeadHosts {
    // host -> ban expiry, so a short cooldown can expire inside the same process
    private val mem = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val hang = mutableMapOf<String, Long>()
    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: android.content.Context) {
        if (prefs == null) {
            prefs = ctx.applicationContext.getSharedPreferences("phantom_net", android.content.Context.MODE_PRIVATE)
        }
    }

    fun hostOf(url: String): String =
        try {
            java.net.URI(url).host.orEmpty()
        } catch (_: Exception) {
            ""
        }

    fun mark(url: String, hours: Long = 24) {
        ban(url, System.currentTimeMillis() + hours * 3_600_000)
    }

    // a 429 is a transient rate limit, not a dead host; keep it out of the 24h list
    fun cooldown(url: String, minutes: Long = 10) {
        ban(url, System.currentTimeMillis() + minutes * 60_000)
    }

    private fun ban(url: String, until: Long) {
        val h = hostOf(url)
        if (h.isEmpty()) return
        mem[h] = until
        try {
            prefs?.edit()?.putLong(h, until)?.apply()
        } catch (_: Exception) {
        }
    }

    fun blocked(url: String): Boolean {
        val h = hostOf(url)
        if (h.isEmpty()) return false
        val now = System.currentTimeMillis()
        val cached = mem[h]
        if (cached != null) {
            if (cached > now) return true
            mem.remove(h)
        }
        val until = try {
            prefs?.getLong(h, 0) ?: 0
        } catch (_: Exception) {
            0
        }
        if (until > now) {
            mem[h] = until
            return true
        }
        return false
    }

    // a hang is not one probe away from fine: the host served the manifest and still stalled,
    // so only its own expiry clears it, never the re-admit probe
    fun markHang(url: String, hours: Long = 2) {
        mark(url, hours)
        val h = hostOf(url)
        if (h.isEmpty()) return
        val until = System.currentTimeMillis() + hours * 3_600_000
        hang[h] = until
        try {
            prefs?.edit()?.putLong("hang|$h", until)?.apply()
        } catch (_: Exception) {
        }
    }

    fun hangBanned(url: String): Boolean {
        val h = hostOf(url)
        if (h.isEmpty()) return false
        val until = hang[h] ?: try {
            prefs?.getLong("hang|$h", 0) ?: 0
        } catch (_: Exception) {
            0
        }
        if (until <= System.currentTimeMillis()) return false
        hang[h] = until
        return true
    }

    fun forgive(url: String) {
        val h = hostOf(url)
        if (h.isEmpty() || hangBanned(url)) return
        mem.remove(h)
        try {
            prefs?.edit()?.remove(h)?.apply()
        } catch (_: Exception) {
        }
    }
}
