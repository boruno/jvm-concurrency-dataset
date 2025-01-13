//package day2

import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicRef<Segment>
    private val tail: AtomicRef<Segment>
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

   companion object {
       private const val SEGM_SIZE = 2
   }

    init {
        val first = Segment(0)
        head = atomic(first)
        tail = atomic(first)
    }

    override fun enqueue(element: E) {
        while(true) {
            val curTail = tail
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = enqIdx.getAndIncrement()
            val segment = findSegment(curTail.value, i / SEGM_SIZE)
            moveTailForward(segment)
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if (segment.arr[i % SEGM_SIZE].compareAndSet(null, element)) {
                return
            }
        }    }

    private fun moveTailForward(segment: Segment) {
        val curTail = tail.value
        if (curTail.id < segment.id)
            tail.compareAndSet(curTail, segment)
    }

    private fun findSegment(start: Segment, id: Int): Segment {
        var curStart = start
        while (id > curStart.id) {
            if (curStart.next.value == null) {
                curStart.next.compareAndSet(null, Segment(curStart.id + 1))
            }
            curStart = curStart.next.value!!
        }
        return curStart
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while(true) {
            val curHead = head
            // Is this queue empty?
            val curDeqIdx = deqIdx.value
            val curEnqIdx = enqIdx.value
            if (curDeqIdx >= curEnqIdx) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.getAndIncrement()
            val segment = findSegment(curHead.value, i / SEGM_SIZE)
//            moveHeadForward(segment)
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            if (segment.arr[i % SEGM_SIZE].compareAndSet(null, POISONED)) {
                continue
            }
            return segment.arr[i % SEGM_SIZE].value as E
        }
    }

    private class Segment(
        val id: Int,
        val arr: AtomicArray<Any?> = atomicArrayOfNulls<Any?>(SEGM_SIZE),
        val next: AtomicRef<Segment?> = atomic(null)
    )
}

private val POISONED = Any()
