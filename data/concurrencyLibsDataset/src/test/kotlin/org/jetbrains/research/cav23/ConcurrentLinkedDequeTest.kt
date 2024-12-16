package org.jetbrains.research.cav23

import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.research.cav23.concurrentLinkedDeque.ConcurrentLinkedDeque

class ConcurrentLinkedDequeTest() : AbstractLincheckTest() {
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
}
