@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class ArrayBlockingQueueTest {
    private val queue = ArrayBlockingQueue<Int>(100)

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
    fun put(e: Int) = queue.put(e)

    @Operation
    fun take() = queue.take()

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun arrayBlockingQueueStressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun arrayBlockingQueueModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
