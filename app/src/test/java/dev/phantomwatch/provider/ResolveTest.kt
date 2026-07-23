package dev.phantomwatch.provider

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveTest {
    private fun bundle(url: String) = Resolve.Bundle(listOf(VideoFormat(url, false, "l", 720)), emptyList(), null)

    @Test
    fun concurrentLoadsShareOneCall() = runBlocking {
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val block: suspend () -> Resolve.Bundle = { calls.incrementAndGet(); gate.await(); bundle("a") }
        val one = async { Resolve.load("t-share", block = block) }
        val two = async { Resolve.load("t-share", block = block) }
        yield()
        gate.complete(Unit)
        assertEquals("a", one.await().formats[0].url)
        assertEquals("a", two.await().formats[0].url)
        assertEquals(1, calls.get())
    }

    @Test
    fun cachedResultSkipsSecondCall() = runBlocking {
        val calls = AtomicInteger()
        val block: suspend () -> Resolve.Bundle = { calls.incrementAndGet(); bundle("b") }
        Resolve.load("t-cache", block = block)
        Resolve.load("t-cache", block = block)
        assertEquals("b", Resolve.load("t-cache", block = block).formats[0].url)
        assertEquals(1, calls.get())
    }

    @Test
    fun freshBypassesCache() = runBlocking {
        val calls = AtomicInteger()
        val block: suspend () -> Resolve.Bundle = { calls.incrementAndGet(); bundle("c") }
        Resolve.load("t-fresh", block = block)
        Resolve.load("t-fresh", fresh = true, block = block)
        assertEquals(2, calls.get())
    }

    @Test
    fun emptyResultIsNotCached() = runBlocking {
        val calls = AtomicInteger()
        val block: suspend () -> Resolve.Bundle = {
            calls.incrementAndGet()
            Resolve.Bundle(emptyList(), emptyList(), null)
        }
        Resolve.load("t-empty", block = block)
        Resolve.load("t-empty", block = block)
        assertEquals(2, calls.get())
    }
}
