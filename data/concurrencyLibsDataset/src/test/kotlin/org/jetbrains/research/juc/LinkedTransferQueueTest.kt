@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class LinkedTransferQueueTest {
    private val queue = LinkedTransferQueue<Int>()

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

    @Operation
    fun tryTransfer(e: Int) = queue.tryTransfer(e)

    @Operation
    fun transfer(e: Int) = queue.transfer(e)

    @Operation
    fun hasWaitingConsumer() = queue.hasWaitingConsumer()

    @Operation
    fun getWaitingConsumerCount() = queue.waitingConsumerCount

    @Operation
    fun isEmpty() = queue.isEmpty()

    @Operation
    fun size() = queue.size

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun linkedTransferQueueStressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun linkedTransferQueueModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
