@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class ConcurrentLinkedDequeTest {
    private val deque = ConcurrentLinkedDeque<Int>()

    @Operation
    fun addFirst(e: Int) = deque.addFirst(e)

    @Operation
    fun addLast(e: Int) = deque.addLast(e)

    @Operation
    fun pollFirst() = deque.pollFirst()

    @Operation
    fun pollLast() = deque.pollLast()

    @Operation
    fun peekFirst() = deque.peekFirst()

    @Operation
    fun peekLast() = deque.peekLast()

    @Test
    fun concurrentLinkedDequeStressTest() = expectInvalidResults {
        StressOptions().check(this::class)
    }

    @Test
    fun concurrentLinkedDequeModelCheckingTest() = expectInvalidResults {
        ModelCheckingOptions().check(this::class)
    }

    private fun expectInvalidResults(block: () -> Unit) {
        try {
            block()
        } catch (e: LincheckAssertionError) {
            println("LincheckAssertionError caught: ${e.message}")
        }
    }
}