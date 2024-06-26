package day2

import day1.*
import kotlinx.atomicfu.*


private const val SEGM_SIZE = 2

class Segment<E>(val id: Int) {

    val next: AtomicRef<Segment<E>?> = atomic(null)

    val elements = atomicArrayOfNulls<E?>(SEGM_SIZE)

//    fun cell(index: Int) = elements[index]
//    operator fun get(index: Int): AtomicRef<E?> = elements[index]

}

class InfiniteArray<E> {

    val headSegment: AtomicRef<Segment<E>>
    val tailSegment: AtomicRef<Segment<E>>


    init {
        val segment = Segment<E>(0)
        headSegment = atomic(segment)
        tailSegment = atomic(segment)
    }


    fun findSegment(start: Segment<E>, idx: Int): Segment<E> {
        val segmentId = idx / SEGM_SIZE
        var curSegment: Segment<E> = start
        while (curSegment.id != segmentId) {
            curSegment = curSegment.next.value ?: addNextSegment(curSegment)
        }
        return curSegment

    }

    private fun addNextSegment(prevSegment: Segment<E>): Segment<E> {
        val newSegment = Segment<E>(prevSegment.id + 1)
        return if (prevSegment.next.compareAndSet(null, newSegment)){
            newSegment
        } else {
            prevSegment.next.value!!
        }
    }

    fun moveHeadForward(segment: Segment<E>) {
        if (headSegment.value.id < segment.id) {
            headSegment.getAndSet(segment)
        }
    }

    fun moveTailForward(segment: Segment<E>) {
        if (tailSegment.value.id < segment.id) {
            tailSegment.getAndSet(segment)
        }

    }


}

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {

    private val infiniteArray = InfiniteArray<Any?>() // infinite array
    private val oldInfiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val curTail = infiniteArray.tailSegment
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = enqIdx.getAndIncrement()
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            val segment = infiniteArray.findSegment(curTail.value, i)
            infiniteArray.moveTailForward(segment)
            if (segment.elements[i % SEGM_SIZE].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while(true) {
            // Is this queue empty?
            if (isEmpty()) return null

            val curHead = infiniteArray.headSegment
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.getAndIncrement()
            val segment = infiniteArray.findSegment(curHead.value, i)
            infiniteArray.moveHeadForward(segment)

            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.

            if (segment.elements[i % SEGM_SIZE].compareAndSet(null, POISONED)) continue
            return segment.elements[i % SEGM_SIZE].value as E
        }
    }

    private fun isEmpty(): Boolean {
        while(true) {
            val curDeqInx = deqIdx.value
            val curEnqInx = enqIdx.value
            if (curDeqInx == deqIdx.value) {
                return curEnqInx <= curDeqInx
            }
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
