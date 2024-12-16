@file:Suppress("HasPlatformType")

package org.jetbrains.research.agrona

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import org.agrona.concurrent.*

class ManyToManyConcurrentArrayQueueTest {
    private val queue = ManyToManyConcurrentArrayQueue<Int>(1024)

    @Operation(nonParallelGroup = "producers")
    fun enqueue(e: Int) = queue.offer(e)

    @Operation(nonParallelGroup = "consumers")
    fun dequeue() = queue.poll()

    @Operation(nonParallelGroup = "consumers")
    fun peek() = queue.peek()

//    @Operation
//    fun isEmpty() = queue.isEmpty()

//    @Operation
//    fun size() = queue.size

    @Test
    fun manyToManyConcurrentArrayQueueStressTest() = StressOptions().check(this::class)

    @Test
    fun manyToManyConcurrentArrayQueueModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
