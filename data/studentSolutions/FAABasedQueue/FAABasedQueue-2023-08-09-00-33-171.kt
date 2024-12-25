//package day2

import day1.Queue
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicRef<Segment<E>>
    private val tail: AtomicRef<Segment<E>>
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        val segment = Segment<E>(0)
        head = atomic(segment)
        tail = atomic(segment)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val segment = findSegment(curTail, i / SEGM_SIZE)
            moveTailForward(segment)
            if (segment.arr[i % SEGM_SIZE].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldTryToDequeue()) return null
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val segment = findSegment(curHead, i / SEGM_SIZE)
            moveHeadForward(segment)
            if (segment.arr[i % SEGM_SIZE].compareAndSet(null, POISONED)) continue
            return segment.arr[i % SEGM_SIZE].value as E
        }
    }

    private fun shouldTryToDequeue(): Boolean {
        while (true) {
            val curEnqIdx = enqIdx.value
            val curDeqIdx = deqIdx.value
            if (curEnqIdx != enqIdx.value) continue
            return curDeqIdx <= curEnqIdx
        }
    }

    private fun findSegment(start: Segment<E>, id: Int): Segment<E> {
        var cur: Segment<E> = start
        while (true) {
            if (cur.id == id) return cur
            val next = cur.next.value
            if (next != null) {
                cur = next
                continue
            }
            val newSegment = Segment<E>(id)
            if (cur.next.compareAndSet(null, newSegment)) {
                return newSegment
            }
            cur = cur.next.value!!
        }
    }

    private fun moveHeadForward(segment: Segment<E>) {
        // TODO
        val curHead = head.value
        val next = curHead.next.value ?: return
        if (next.id > curHead.id) {
            head.compareAndSet(curHead, next)
        }
    }

    private fun moveTailForward(segment: Segment<E>) {
        // TODO incorrect
        tail.value = segment
    }

    private class Segment<E>(val id: Int) {
        val arr = atomicArrayOfNulls<Any?>(SEGM_SIZE)
        val next: AtomicRef<Segment<E>?> = atomic(null)
    }
}

private val POISONED = Any()
private const val SEGM_SIZE = 8