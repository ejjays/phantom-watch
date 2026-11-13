package dev.phantomwatch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {
    private val apk = "phantomwatch-1.0.313.apk"
    private val url = "https://github.com/ejjays/phantom-watch/releases/download/v1/$apk"

    private fun release(tag: String, draft: Boolean, asset: String, link: String) = """
        {"tag_name":"$tag","draft":$draft,"assets":[{"name":"$asset","size":12345,"browser_download_url":"$link"}]}
    """.trimIndent()

    private fun entry(tag: String, draft: Boolean, asset: String, link: String) =
        release(tag, draft, asset, link)

    @Test
    fun readsTheCodeFromTheAssetName() {
        val parsed = UpdateChecker.parse("[${entry("v1.0.0-beta.1", false, apk, url)}]")
        assertEquals("v1.0.0-beta.1", parsed?.tag)
        assertEquals(313L, parsed?.code)
        assertEquals(12345L, parsed?.size)
    }

    @Test
    fun skipsDrafts() {
        assertNull(UpdateChecker.parse("[${entry("v1.2.0", true, apk, url)}]"))
    }

    @Test
    fun theBuildNumberIsTheLargestNumberInTheName() {
        val named = entry("v1.2.0", false, "phantomwatch-1.0.374.apk", url)
        assertEquals(374L, UpdateChecker.parse("[$named]")?.code)
    }

    @Test
    fun aBareAppReleaseFallsBackToTheTag() {
        val plain = "https://github.com/ejjays/phantom-watch/releases/download/v1/app-release.apk"
        val tagged = entry("v1.0.374", false, "app-release.apk", plain)
        assertEquals(374L, UpdateChecker.parse("[$tagged]")?.code)
    }

    @Test
    fun skipsReleasesWithNoBuildNumberAnywhere() {
        val plain = "https://github.com/ejjays/phantom-watch/releases/download/v1/app-release.apk"
        assertNull(UpdateChecker.parse("[${entry("latest", false, "app-release.apk", plain)}]"))
    }

    @Test
    fun skipsAssetsThatAreNotApks() {
        assertNull(UpdateChecker.parse("[${entry("v1.2.0", false, "phantomwatch-1.0.400.zip", url)}]"))
    }

    @Test
    fun skipsAssetsFromAnotherHost() {
        val foreign = "https://example.com/phantomwatch-1.0.400.apk"
        assertNull(UpdateChecker.parse("[${entry("v1.2.0", false, apk, foreign)}]"))
    }

    @Test
    fun newestUsableReleaseWins() {
        val newer = entry("v1.3.0", false, "phantomwatch-1.0.500.apk", url)
        val older = entry("v1.2.0", false, "phantomwatch-1.0.400.apk", url)
        assertEquals(500L, UpdateChecker.parse("[$newer,$older]")?.code)
    }

    @Test
    fun theHigherBuildWinsEvenWhenListedSecond() {
        val older = entry("v1.2.0", false, "phantomwatch-1.0.400.apk", url)
        val newer = entry("v1.3.0", false, "phantomwatch-1.0.500.apk", url)
        assertEquals(500L, UpdateChecker.parse("[$older,$newer]")?.code)
    }

    @Test
    fun aDraftAtTheTopFallsThroughToTheNextRelease() {
        val draft = entry("v1.4.0", true, "phantomwatch-1.0.600.apk", url)
        val live = entry("v1.3.0", false, "phantomwatch-1.0.500.apk", url)
        assertEquals(500L, UpdateChecker.parse("[$draft,$live]")?.code)
    }

    @Test
    fun anEmptyListGivesNoRelease() {
        assertNull(UpdateChecker.parse("[]"))
    }
}
