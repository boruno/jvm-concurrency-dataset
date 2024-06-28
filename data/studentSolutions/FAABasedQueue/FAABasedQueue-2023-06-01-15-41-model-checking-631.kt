package day2

import day1.*
import kotlinx.atomicfu.*
import javax.swing.text.Segment


// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    private val head: AtomicRef<SegmentNode<E>>
    private val tail: AtomicRef<SegmentNode<E>>

    init {
        val dummy = SegmentNode<E>(-1)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private fun findSegment(start: SegmentNode<E>, id: Long): SegmentNode<E> {
        val newSegment = SegmentNode<E>(id)
        var s: SegmentNode<E> = start
        while (s.id != id) {
            check(s.id < id)
            s.next.compareAndSet(null, newSegment)
            s = s.next.value!!
        }
        return s
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val id = i / SEGMENT_SIZE
            val index = (i % SEGMENT_SIZE).toInt()

            val s = findSegment(curTail, id)
            tail.compareAndSet(curTail, s)

            if (s.arr[index].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val id = i / SEGMENT_SIZE
            val index = (i % SEGMENT_SIZE).toInt()

            val s = findSegment(curHead, id)
            head.compareAndSet(curHead, s)

            if (s.arr[index].compareAndSet(null, POISONED)) {
                continue
            }

            return s.arr[index].value as E
        }
    }

    private class SegmentNode<E>(
        val id: Long,
        val poisoned: Boolean = false
    ) {
        val next = atomic<SegmentNode<E>?>(null)
        val arr = atomicArrayOfNulls<Any?>(SEGMENT_SIZE)
    }
}

private val POISONED = Any()

private val SEGMENT_SIZE : Int = 2
