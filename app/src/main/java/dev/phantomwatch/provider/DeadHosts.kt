package dev.phantomwatch.provider

object DeadReleases {
    val hashes = mutableSetOf<String>()
    val urls = mutableSetOf<String>()
}

object DeadHosts {
    // host to ban expiry so a short cooldown expires inside the process
    private val mem = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var prefs: android.content.SharedPreferences? = null
    // hosts of top leg, a hang there costs 1080p subs and dubs
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

    // 429 is a rate limit not a dead host, keep it out of the 24h list
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

    // hang is a short ban like the rest, readmit probe lifts it once host
    // streams manifest again so one bad edge cant hide provider for the session
    fun markHang(url: String, minutes: Long = 120): Boolean {
        if (kept(url)) return false
        ban(url, System.currentTimeMillis() + minutes * 60_000)
        return true
    }

    // resolver calls this for head of leg order
    fun keepHost(url: String) {
        val h = hostOf(url)
        if (h.isNotEmpty()) keep.add(h)
    }

    fun kept(url: String): Boolean = hostOf(url) in keep

    // called only when probe streams real manifest bytes never on a header
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
