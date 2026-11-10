package dev.phantomwatch.agent

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// the service cannot navigate the activity, so a play call leaves the request here instead
object PlayRequest {
    const val EXTRA_ID = "pw_play_id"
    const val EXTRA_KIND = "pw_play_kind"
    const val EXTRA_TITLE = "pw_play_title"
    const val EXTRA_SEASON = "pw_play_season"
    const val EXTRA_EPISODE = "pw_play_episode"

    data class Ask(
        val id: String,
        val kind: String,
        val title: String,
        val season: String,
        val episode: String,
    )

    var pending by mutableStateOf<Ask?>(null)
        private set

    // a blocked background start never reaches take(), so this is also the "did the UI open" flag
    @Volatile
    var adopted = false

    fun put(intent: Intent, ask: Ask): Intent = intent
        .putExtra(EXTRA_ID, ask.id)
        .putExtra(EXTRA_KIND, ask.kind)
        .putExtra(EXTRA_TITLE, ask.title)
        .putExtra(EXTRA_SEASON, ask.season)
        .putExtra(EXTRA_EPISODE, ask.episode)

    fun take(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_ID) ?: return
        adopted = true
        pending = Ask(
            id,
            intent.getStringExtra(EXTRA_KIND).orEmpty(),
            intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            intent.getStringExtra(EXTRA_SEASON).orEmpty(),
            intent.getStringExtra(EXTRA_EPISODE).orEmpty(),
        )
        intent.removeExtra(EXTRA_ID)
    }

    fun consume(): Ask? = pending.also { pending = null }
}
