//package day2

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

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val id = i / SEGMENT_SIZE

            val newSegment = SegmentNode<E>(id)
            var s = curTail
            while (s.id != id) {
                check(s.id < id)
                s.next.compareAndSet(null, newSegment)
                s = s.next.value!!
            }
            tail.compareAndSet(curTail, s)
            if (s.poisoned) continue

            val index = (i % SEGMENT_SIZE).toInt()
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
            var s = curHead
            val poisonedSegment = SegmentNode<E>(id, true)
            while (s.id != id) {
                check(s.id < id)
                s.next.compareAndSet(null, poisonedSegment)
                s = s.next.value!! // should be not null because of check enqIdx before
            }
            head.compareAndSet(curHead, s)
            if (s.poisoned) continue

            val index = (i % SEGMENT_SIZE).toInt()
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
