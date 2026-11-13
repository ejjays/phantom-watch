package dev.phantomwatch.provider

object DeadReleases {
    val hashes = mutableSetOf<String>()
    val urls = mutableSetOf<String>()
}

object DeadHosts {
    // host -> ban expiry, so a short cooldown can expire inside the same process
    private val mem = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var prefs: android.content.SharedPreferences? = null
    // hosts of the top leg; a hang there would cost 1080p, the subs and the dubs
    private val keep = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

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

    // a hang is a short ban like any other: the re-admit probe can lift it as soon as the host
    // streams the manifest again, so one bad edge cannot hide the provider for the session
    fun markHang(url: String, minutes: Long = 120): Boolean {
        if (kept(url)) return false
        ban(url, System.currentTimeMillis() + minutes * 60_000)
        return true
    }

    // called by the resolver for the head of the leg order
    fun keepHost(url: String) {
        val h = hostOf(url)
        if (h.isNotEmpty()) keep.add(h)
    }

    fun kept(url: String): Boolean = hostOf(url) in keep

    // called only once the probe streams real bytes of the manifest, never on a header alone
    fun forgive(url: String) {
        val h = hostOf(url)
        if (h.isEmpty()) return
        mem.remove(h)
        try {
            prefs?.edit()?.remove(h)?.apply()
        } catch (_: Exception) {
        }
    }
}
