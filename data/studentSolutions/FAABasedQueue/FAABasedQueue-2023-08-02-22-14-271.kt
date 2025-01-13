//package day2

import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicRef<Segment<E>>
    private val tail: AtomicRef<Segment<E>>
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        val first = Segment<E>(0)
        head = atomic(first)
        tail = atomic(first)
    }

    private fun shouldNotTryToDequeue(): Boolean {
        while (true) {
            val curDeqIdx = deqIdx.value
            val curEnqIdx = enqIdx.value
            if (curDeqIdx != deqIdx.value) continue
            return curDeqIdx >= curEnqIdx
        }
    }

    override fun enqueue(element: E) {
        while(true) {
            val curTail = tail
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = enqIdx.getAndIncrement()
            val segment = findSegment(curTail.value, i / SEGM_SIZE)
            if (i % SEGM_SIZE == SEGM_SIZE - 1) {
                moveTailForward(segment)
            }
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if (segment.elements[i].compareAndSet(null, element)) {
                return
            }
        }
    }

    private fun moveTailForward(segment: Segment<E>) {
        while (true) {
            val curTail = tail.value
            val nextTail = curTail.next.value ?: Segment(curTail.id + 1)
            if (curTail.next.compareAndSet(null, nextTail)) {
                tail.compareAndSet(curTail, nextTail)
                return
            }
            if (tail.compareAndSet(curTail, curTail.next.value!!)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while(true) {
            val curHead = head
            // Is this queue empty?
            if (shouldNotTryToDequeue()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.getAndIncrement()
            val segment = findSegment(curHead.value, i / SEGM_SIZE)
            if (i % SEGM_SIZE == SEGM_SIZE - 1) {
                moveHeadForward(segment)
            }
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            if (segment.elements[i].compareAndSet(null, POISONED)) {
                continue
            }
            return segment.elements[i].value as E
        }
        return null
    }

    private fun moveHeadForward(segment: Segment<E>) {
        while(true) {
            val curHead = head.value
            val nextHead = curHead.next.value ?: Segment(curHead.id + 1)
            if (curHead.next.compareAndSet(null, nextHead)) {
                head.compareAndSet(curHead, nextHead)
                return
            }
            if (head.compareAndSet(curHead, curHead.next.value!!)) return
        }
    }

    private fun findSegment(start: Segment<E>, id: Int): Segment<E> {
        var curStart = start
        while (id > curStart.id) {
            val next = curStart.next.value ?: Segment(curStart.id + 1)
            curStart.next.compareAndSet(null, next)
            curStart = curStart.next.value!!
        }
        return curStart
    }

    private class Segment<E>(val id: Int) {
        val elements: AtomicArray<Any?> = atomicArrayOfNulls(SEGM_SIZE)
        val next = atomic<Segment<E>?>(null)
    }

    companion object {
        private const val SEGM_SIZE = 2
    }

    // TODO: poison cells with this value.
    private val POISONED = Any()
}