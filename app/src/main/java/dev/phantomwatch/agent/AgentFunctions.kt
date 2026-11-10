package dev.phantomwatch.agent

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import dev.phantomwatch.MainActivity
import dev.phantomwatch.data.Search
import dev.phantomwatch.data.Stream
import dev.phantomwatch.player.Playback
import dev.phantomwatch.player.sourceFor
import dev.phantomwatch.provider.Providers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "AgentService",
    appFunctionXmlFileName = "phantom_agent_functions",
)
abstract class AgentFunctions : AppFunctionService() {
    @AppFunctionDeclaration(isDescribedByKDoc = false)
    fun searchTitles(query: String): String = runBlocking(Dispatchers.IO) {
        runCatching {
            Search.titles(query)
                .take(10)
                .joinToString("\n") { "${it.kind}:${it.id}:${it.title}" }
        }.getOrElse { "error: ${it::class.simpleName}: ${it.message}" }
    }

    // the screen is best effort: a background app is not allowed to bring itself up
    @AppFunctionDeclaration(isDescribedByKDoc = false)
    fun play(tmdbId: String, kind: String, title: String, season: String, episode: String): String {
        val ctx = applicationContext
        val ask = PlayRequest.Ask(tmdbId, kind, title, season, episode)
        PlayRequest.adopted = false
        runCatching {
            startActivity(
                PlayRequest.put(Intent(this, MainActivity::class.java), ask)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        state = "resolving"
        work.launch {
            var waited = 0
            while (!PlayRequest.adopted && waited < 3_000) {
                delay(100)
                waited += 100
            }
            if (PlayRequest.adopted) {
                state = "ui"
                return@launch
            }
            runCatching { playHere(ctx, ask) }
                .onFailure { state = "error: ${it::class.simpleName}: ${it.message}" }
        }
        return "resolving $title"
    }

    private suspend fun playHere(ctx: Context, ask: PlayRequest.Ask) {
        val player = Playback.ensure(ctx)
        val (formats, subs, err) = withContext(Dispatchers.IO) {
            Providers.directAll(ask.id, ask.kind, ask.season, ask.episode, ask.title)
        }
        if (PlayRequest.adopted) {
            state = "ui"
            return
        }
        val best = formats.firstOrNull() ?: error("no source ${err.orEmpty()}")
        val streamSubs = Providers.subsFor(best, subs)
        val stream = Stream(
            best.url, best.isHls, streamSubs, best.referer, null,
            headers = best.headers, enSubs = best.enSubs, intro = best.intro, dub = best.dub,
        )
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        // a background app loses the focus request, which clears playWhenReady
        player.setAudioAttributes(attrs, false)
        player.setMediaSource(sourceFor(ctx, stream, streamSubs, null, null))
        player.prepare()
        var prep = 0
        while (player.playbackState != Player.STATE_READY && prep < 15_000) {
            delay(200)
            prep += 200
        }
        player.play()
        var started = 0
        while (!player.isPlaying && started < 5_000) {
            delay(200)
            started += 200
        }
        // hand focus back so a call still pauses normal playback
        player.setAudioAttributes(attrs, true)
        state = "${best.label} ${best.height}p"
    }

    @AppFunctionDeclaration(isDescribedByKDoc = false)
    fun playbackState(): String {
        val p = Playback.player ?: return state
        return "$state state=${p.playbackState} pos=${p.currentPosition} dur=${p.duration} " +
            "pw=${p.playWhenReady} playing=${p.isPlaying} err=${p.playerError?.errorCodeName.orEmpty()}"
    }

    private companion object {
        // outlives the unbind that follows a function returning
        val work = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        @Volatile
        var state = "idle"
    }
}
