package dev.phantomwatch.lan

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Sub
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

// owns the one server instance, so the settings page, the player hook and onDispose never talk
// to the socket directly
internal object LanShare {
    data class Session(val stream: Stream, val subs: List<Sub>)

    sealed interface State {
        data object Idle : State
        data object Starting : State
        data class Running(val urls: List<String>) : State
        data class Failed(val why: String) : State
    }

    var state: State by mutableStateOf<State>(State.Idle)
        private set

    @Volatile
    private var session: Session? = null
    private var server: LanServer? = null

    // called from sourceFor on every attach, so it must stay allocation-light and non-blocking
    fun onStream(stream: Stream, subs: List<Sub>) {
        session = if (stream.url.startsWith("http")) {
            Session(stream, subs.filter { it.url.startsWith("http") })
        } else {
            null
        }
    }

    fun start(context: Context) {
        if (session == null) {
            state = State.Failed("Nothing to share yet")
            return
        }
        stop()
        state = State.Starting
        val token = token()
        val srv = LanServer(token) { session }
        val port = runCatching { srv.start() }.getOrElse {
            state = State.Failed("Could not open a port")
            return
        }
        val urls = addresses(context).map { "http://$it:$port/$token/" }
        if (urls.isEmpty()) {
            srv.stop()
            state = State.Failed("Not on Wi-Fi")
            return
        }
        server = srv
        state = State.Running(urls)
        AppLog.d("lan ${urls.first()}")
    }

    fun stop() {
        server?.stop()
        server = null
        if (state != State.Idle) state = State.Idle
    }

    private fun token(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        return buildString(TOKEN_LEN) {
            repeat(TOKEN_LEN) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    // Wi-Fi first, then any other live IPv4 interface: a hotspot is not modelled as a network
    private fun addresses(context: Context): List<String> {
        val out = LinkedHashSet<String>()
        wifiAddress(context)?.let(out::add)
        out.addAll(interfaceAddresses())
        return out.filter { it.isNotEmpty() }
    }

    private fun wifiAddress(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = cm.activeNetwork ?: return null
        if (cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return null
        return cm.getLinkProperties(net)?.linkAddresses
            ?.mapNotNull { (it.address as? Inet4Address)?.takeIf { a -> !a.isLoopbackAddress }?.hostAddress }
            ?.firstOrNull()
    }

    private fun interfaceAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && SKIP.none(it.name::startsWith) }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .mapNotNull { it.hostAddress }
            .toList()
    }.getOrDefault(emptyList())

    private const val TOKEN_LEN = 8
    private val SKIP = listOf("rmnet", "tun", "ppp", "dummy")
}
