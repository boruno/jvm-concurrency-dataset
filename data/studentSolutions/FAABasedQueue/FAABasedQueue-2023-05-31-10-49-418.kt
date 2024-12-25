//package day2

import day1.*
import kotlinx.atomicfu.*

private const val SEGM_SIZE = 16

// TODO: Copy the code from `FAABasedQueueSimplified` and implement the infinite array on a linked list of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private class Segment<E>(val id: Int) {
        val arr = atomicArrayOfNulls<Any?>(SEGM_SIZE)
        val next = atomic<Segment<E>?>(null)
    }

    private val head = atomic(Segment<E>(0))
    private val tail = atomic(Segment<E>(0))
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    private fun findSegment(start: Segment<E>, id: Int): Segment<E> {
        var s = start
        while (s.id < id) {
            // try to advance to the next segment
            val next = s.next.value
            if (next != null) {
                s = next
            } else {
                // try to append a new segment
                s.next.compareAndSet(null, Segment(id))
                s = s.next.value!!
            }
        }
        return s
    }

    private fun moveTailForward(s: Segment<E>) {
        while (true) {
            val tailSnapshot = tail.value
            if (tailSnapshot.id >= s.id || tail.compareAndSet(tailSnapshot, s)) {
                return
            }
        }
    }

    private fun moveHeadForward(s: Segment<E>) {
        while (true) {
            val headSnapshot = head.value
            if (headSnapshot.id >= s.id || head.compareAndSet(headSnapshot, s)) {
                return
            }
        }
    }

    override fun enqueue(element: E) {
        while (true) {
            val cur_tail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(cur_tail, i / SEGM_SIZE)
            moveTailForward(s)
            if (s.arr[i % SEGM_SIZE].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            val cur_head = head.value
            val i = deqIdx.getAndIncrement()
            val s = findSegment(cur_head, i / SEGM_SIZE)
            moveHeadForward(s)
            if (s.arr[i % SEGM_SIZE].compareAndSet(null, POISONED)) {
                continue
            }
            return s.arr[i % SEGM_SIZE].value as E?
        }
    }
}

private val POISONED = Any()