@file:Suppress("HasPlatformType")

package org.jetbrains.research.jctools

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import org.jctools.queues.unpadded.SpscLinkedUnpaddedQueue

class SpscLinkedUnpaddedQueueTest {
    private val queue = SpscLinkedUnpaddedQueue<Int>()

    @Operation(nonParallelGroup = "producers")
    fun enqueue(e: Int) = queue.offer(e)

    @Operation(nonParallelGroup = "consumers")
    fun dequeue() = queue.poll()

    @Operation(nonParallelGroup = "consumers")
    fun peek() = queue.peek()

//    @Operation // TODO: check linearizability of isEmpty
//    fun isEmpty() = queue.isEmpty
//
//    @Operation // TODO: check linearizability of size
//    fun size() = queue.size

    @Test
    fun spscLinkedUnpaddedQueueStressTest() = StressOptions().check(this::class)

    @Test
    fun spscLinkedUnpaddedQueueModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
