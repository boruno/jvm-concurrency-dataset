package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    private val brokenElement = Any()

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    fun enqueue(element: E) {
        while (true) {
            val cur_tail = tail.value
            val cur_tail_enqIdx = cur_tail.enqIdx.getAndIncrement()

            if (cur_tail_enqIdx > SEGMENT_SIZE) { // moveTail
                cur_tail.next.compareAndSet(null, Segment())
                tail.compareAndSet(cur_tail, cur_tail.next.value!!)
                continue
            }

            if (cur_tail.elements[cur_tail_enqIdx].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) {
                return null
            }

            val cur_head = head.value
            val cur_head_deqIdx = cur_head.deqIdx.getAndIncrement()

            if (cur_head_deqIdx >= SEGMENT_SIZE) { // moveHead
                if (cur_head.next.value != null) {
                    head.compareAndSet(cur_head, cur_head.next.value!!)
                }
                continue
            }

            if (cur_head.elements[cur_head_deqIdx].compareAndSet(null, brokenElement)) {
                continue
            }

            val result = cur_head.elements[cur_head_deqIdx].value
            if (result === brokenElement) {
                continue
            }
            return result as E
        }
    }

    /**
     * Returns true if this queue is empty, or false otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val cur_head = head.value
                val cur_enqIdx = cur_head.enqIdx.value // минимальное значение за промежуток времени
                val cur_deqIdx = cur_head.deqIdx.value // максимальное значение за промежуток времени

                if (cur_deqIdx >= SEGMENT_SIZE) {
                    cur_head.elements[0].compareAndSet(null, brokenElement)
                    cur_head.elements[1].compareAndSet(null, brokenElement)

                    if (cur_head.next.value == null) {
                        return true
                    }
                    else { // help
                        head.compareAndSet(cur_head, cur_head.next.value!!)
                    }
                }
                else {
                    return cur_enqIdx <= cur_deqIdx
                }
            }
        }
}

private class Segment() {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val enqIdx = atomic(0)
    val deqIdx = atomic(0)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
