@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class PriorityBlockingQueueTest {
    private val queue = PriorityBlockingQueue<Int>()

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
    fun priorityBlockingQueueStressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun priorityBlockingQueueModelCheckingTest() = ModelCheckingOptions().check(this::class)
}

