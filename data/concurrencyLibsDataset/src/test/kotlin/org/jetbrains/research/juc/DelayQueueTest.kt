@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class DelayQueueTest {
    private val queue = DelayQueue<DelayedElement>()

    class DelayedElement(private val delay: Long) : Delayed {
        private val startTime = System.currentTimeMillis() + delay

        override fun getDelay(unit: TimeUnit): Long {
            val diff = startTime - System.currentTimeMillis()
            return unit.convert(diff, TimeUnit.MILLISECONDS)
        }

        override fun compareTo(other: Delayed): Int {
            return if (this.startTime < (other as DelayedElement).startTime) -1 else 1
        }
    }

    @Operation
    fun add(e: Long) = queue.add(DelayedElement(e))

    @Operation
    fun poll() = queue.poll()

    @Operation
    fun peek() = queue.peek()

    @Operation
    fun take() = queue.take()

    @Operation
    fun size() = queue.size

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun delayQueueStressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun delayQueueModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
