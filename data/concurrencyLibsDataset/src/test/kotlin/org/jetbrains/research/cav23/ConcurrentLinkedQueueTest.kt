package org.jetbrains.research.cav23

import org.jetbrains.kotlinx.lincheck.Options
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.research.cav23.concurrentLinkedQueue.ConcurrentLinkedQueue

class ConcurrentLinkedQueueTest : AbstractLincheckTest() {
    private val queue = ConcurrentLinkedQueue<Int>()

    @Operation
    fun add(e: Int) = queue.add(e)

    @Operation
    fun offer(e: Int) = queue.offer(e)

    @Operation
    fun peek() = queue.peek()

    @Operation
    fun poll() = queue.poll()

    override fun <O : Options<O, *>> O.customize() {
        actorsBefore(0)
        actorsAfter(0)
    }
}