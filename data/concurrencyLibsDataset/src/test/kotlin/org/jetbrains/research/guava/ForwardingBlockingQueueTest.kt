@file:Suppress("HasPlatformType")

package org.jetbrains.research.guava

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import com.google.common.collect.*
import com.google.common.util.concurrent.ForwardingBlockingQueue
import java.util.concurrent.*

class ForwardingBlockingQueueTest {
    private val queue = CustomForwardingBlockingQueue()

    @Operation
    fun add(e: Int) = queue.add(e)

    @Operation
    fun remove() = queue.remove()

    @Operation
    fun offer(e: Int) = queue.offer(e)

    @Operation
    fun poll() = queue.poll()

    @Operation
    fun peek() = queue.peek()

    @Operation
    fun isEmpty() = queue.isEmpty()

    @Operation
    fun size() = queue.size

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun forwardingBlockingQueueStressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun forwardingBlockingQueueModelCheckingTest() = ModelCheckingOptions().check(this::class)

    private class CustomForwardingBlockingQueue : ForwardingBlockingQueue<Int>() {
        private val delegate = LinkedBlockingQueue<Int>()

        override fun delegate(): BlockingQueue<Int> = delegate
    }
}