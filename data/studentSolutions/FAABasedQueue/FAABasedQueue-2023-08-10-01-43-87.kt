//package day2

import day1.*
import java.util.concurrent.atomic.*

private class InfiniteArray {
    private val nextId = AtomicLong(0)
    private val head: AtomicReference<Segment>
    private val tail: AtomicReference<Segment>

    init {
        val initialSegment = Segment(getNewSegmentId())
        head = AtomicReference(initialSegment)
        tail = AtomicReference(initialSegment)
    }

    val currentHead get() = head.get()
    val currentTail get() = tail.get()

    fun getAndSet(index: Long, newValue: Any?): Any? {
        val currentHead = head.get()
        val segment = findSegment(currentHead ,segmentId(index))

        return segment.cells.getAndSet(itemIndex(index), newValue)
    }

    fun compareAndSetHead(index: Long, expectedValue: Any?, value: Any?): Boolean {
        val currentHead = head.get()

        val expectedSegmentId = segmentId(index)
        if (expectedSegmentId < currentHead.id) {
            return false
        }

        val segment = findSegment(currentHead, id = expectedSegmentId)

        moveHeadForward(segment)

        return segment.cells.compareAndSet(itemIndex(index), expectedValue, value)
    }

    fun compareAndSetTail(index: Long, expectedValue: Any?, value: Any?): Boolean {
        val currentTail = tail.get()

        val expectedSegmentId = segmentId(index)
        if (expectedSegmentId < currentTail.id) {
            return false
        }

        val segment = findSegment(currentTail, id = expectedSegmentId)

        moveTailForward(segment)

        return segment.cells.compareAndSet(itemIndex(index), expectedValue, value)
    }

    fun findSegment(start: Segment, id: Long): Segment {
        var position = start
        while (position.id != id) { // todo: it could happen that we skip some ids
            position = position.next.get() ?: createSegment(position)
        }

        return position
    }

    fun moveTailForward(segment: Segment) = moveForward(segment, tail)
    fun moveHeadForward(segment: Segment) = moveForward(segment, head)
    private fun moveForward(segment: Segment, reference: AtomicReference<Segment>) {
        while (true) {
            val currentValue = reference.get()
            if (currentValue.id < segment.id) {
                if (reference.compareAndSet(currentValue, segment)) {
                    return
                }
            } else {
                return
            }
        }
    }

    fun segmentId(index: Long) = index / SEGMENT_SIZE

    fun itemIndex(index: Long) = (index % SEGMENT_SIZE).toInt()

    private fun createSegment(parent: Segment): Segment {
        val newSegment = Segment(getNewSegmentId())
        return if (parent.next.compareAndSet(null, newSegment)) {
            newSegment
        } else parent.next.get()!!
    }

    private fun getNewSegmentId(): Long = nextId.getAndIncrement()
}

class FAABasedQueue<E> : Queue<E> {
    private val infiniteArray = InfiniteArray() // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val lastSetEnqIndex = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            // Increment the counter atomically via Fetch-and-Add.
            val i = enqIdx.getAndIncrement()

            // Atomically install the element into the cell if the cell is not poisoned.
            if (infiniteArray.compareAndSetTail(i, null, element)) {
                lastSetEnqIndex.compareAndSet(i - 1, i)
                return
            }

//            val currentTail = infiniteArray.currentTail
//            val i = enqIdx.getAndIncrement()
//            val segment = infiniteArray.findSegment(currentTail, infiniteArray.segmentId(i))
//
//            infiniteArray.moveTailForward(segment)
//
//            if (segment.cells.compareAndSet(infiniteArray.itemIndex(i), null, element)) {
//                return
//            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (!shouldDequeue()) return null

            // Increment the counter atomically via Fetch-and-Add.
            val i = deqIdx.getAndIncrement()

            // Try to retrieve an element if the cell contains an element, poisoning the cell if it is empty.
            if (infiniteArray.compareAndSetHead(i, null, POISONED)) {
                continue
            }

            return infiniteArray.getAndSet(i, null) as? E

//            if (!shouldDequeue()) return null
//
//            val currentHead = infiniteArray.currentHead
//            val i = deqIdx.getAndIncrement()
//            val segment = infiniteArray.findSegment(currentHead, infiniteArray.segmentId(i))
//
//            infiniteArray.moveHeadForward(segment)
//
//            val itemIndex = infiniteArray.itemIndex(i)
//
//            if (segment.cells.compareAndSet(itemIndex, null, POISONED)) {
//                continue
//            }
//
//            return segment.cells.get(itemIndex) as? E
        }
    }

    private fun shouldDequeue(): Boolean {
        while (true) {
            val enqueueIdx = lastSetEnqIndex.get()
            val dequeueIdx = deqIdx.get()
            if (lastSetEnqIndex.get() != enqueueIdx) { continue }
            return dequeueIdx < enqueueIdx // todo: and enqueue idx set
        }
    }
}

private val POISONED = Any()

private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2
