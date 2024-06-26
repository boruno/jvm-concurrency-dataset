package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    class Segment<E>(id: Long? = null) {
        companion object {
            val size = 1
        }

        val items = atomicArrayOfNulls<Any?>(size) // Array to hold items
        val next = atomic<Segment<E>?>(null) // Reference to the next segment
        var id = id ?: -1L
    }

    private val headSegment = atomic(Segment<E>(0))
    private val tailSegment = atomic (headSegment.value)
    private val deqIdx = atomic(0L)
    private val enqIdx = atomic(0L)

    private fun addLastSegment(): Segment<E> {
        while (true) {
            val curTail = tailSegment.value
            val segment = Segment<E>(curTail.id + 1)
            if (curTail.next.compareAndSet(null, segment)) {
                tailSegment.compareAndSet(curTail, segment)
                return segment
            } else {
                tailSegment.compareAndSet(curTail, curTail.next.value!!)
            }
        }
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tailSegment.value
            val i = enqIdx.getAndIncrement()
            val segment = findSegment(curTail, i / Segment.size)
            if (segment.items[(i % Segment.size).toInt()].compareAndSet(null, element)) return
        }
    }

    private fun findSegment(start: Segment<E>, id: Long): Segment<E> {
        if (start.id == id) return start
        while (true) {
            val addedSegment = addLastSegment()
            if (addedSegment.id == id) return addedSegment
            if (addedSegment.id < id) continue
            var curSeg = headSegment.value
            while (true) {
                if (curSeg.id == id) return curSeg
                curSeg = curSeg.next.value!!
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            val curHead = headSegment.value
            val i = deqIdx.getAndIncrement()
            val segment = findSegment(curHead, i / Segment.size)
            moveHead(segment)
            if (segment.items[(i % Segment.size).toInt()].compareAndSet(null, POISONED))
                continue
            return segment.items[(i % Segment.size).toInt()].value as E?
        }
    }

    private fun moveHead(segment: Segment<E>) {
        val curHead = headSegment.value
        headSegment.compareAndSet(curHead, segment)
    }
}