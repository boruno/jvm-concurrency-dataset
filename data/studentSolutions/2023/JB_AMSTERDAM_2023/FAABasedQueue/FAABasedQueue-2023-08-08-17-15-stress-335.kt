package day2

import day1.*
import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.


private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2

class ConceptuallyInfiniteArray {
    private val head = Segment(0)

    private fun segmentForIndex(i: Long): Long {
        return i / SEGMENT_SIZE
    }

    private fun indexInSegment(i: Long): Long {
        return i % SEGMENT_SIZE
    }

    private fun findSegmentWithId(id: Long): Segment {
        var curr: Segment = head
        while (true) {
            val next = curr.next.get()
//            println("looking for $id in ${curr.id}, has ${curr.next}")
            if (curr.id == id) return curr
            if (next == null) {
                // we're at the tail
                curr.next.compareAndSet(null, Segment(id))
                // it doesn't matter whether it's us or someone else who created the segment
                // but we make sure we read the _actual_ next pointer.
                // (which we're sure is set at this point, either by us or someone else)
                return curr.next.get()!!
            } else {
                // we're not at the tail yet
                curr = next
            }
        }
    }

    fun compareAndSet(i: Int, expectedValue: Any?, newValue: Any?): Boolean {
        val seg = findSegmentWithId(segmentForIndex(i.toLong()))
        return seg.cells.compareAndSet(indexInSegment(i.toLong()).toInt(), expectedValue, newValue)
    }

    fun get(i: Int): Any? {
        val seg = findSegmentWithId(segmentForIndex(i.toLong()))
        return seg.cells.get(indexInSegment(i.toLong()).toInt())
    }

    fun set(i: Int, value: Any?) {
        val seg = findSegmentWithId(segmentForIndex(i.toLong()))
        return seg.cells.set(indexInSegment(i.toLong()).toInt(), value)
    }
}

class FAABasedQueue<E> : Queue<E> {

    private val infiniteArray = ConceptuallyInfiniteArray() // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = enqIdx.getAndIncrement()
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if (infiniteArray.compareAndSet(i.toInt(), null, element)) {
                // inserted properly
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (deqIdx.get() >= enqIdx.get()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.getAndIncrement()
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            val isPoisoned = infiniteArray.compareAndSet(i.toInt(), null, POISONED)
            if (isPoisoned) {
                continue
            } else {
                // we read a null, but a value was written in the meantime
                // "the poisoning failed"
                // that means we do have a value tho
                val retval = infiniteArray.get(i.toInt())
                infiniteArray.set(i.toInt(), null)
                return retval as E
            }
        }
    }
}


private val POISONED = Any()