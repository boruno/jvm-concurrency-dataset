//package day2

import day1.*
import java.util.concurrent.atomic.*

private class InfiniteArray {
    private val nextId = AtomicLong(0)
    private val head: AtomicReference<Segment>
    private val tail: AtomicReference<Segment>

    init {
        val initialSegment = AtomicReference(Segment(nextId.getAndIncrement()))
        head = initialSegment
        tail = initialSegment
    }

    fun getAndSet(index: Long, newValue: Any?): Any? {
        val currentHead = head.get()
        val segment = findSegment(currentHead ,segmentId(index))

        return segment.cells.getAndSet(itemIndex(index), newValue)
    }

    fun compareAndSetHead(index: Long, expectedValue: Any?, value: Any?): Boolean {
        val currentHead = head.get()
        val segment = findSegment(currentHead, id = segmentId(index))

        moveHeadForward(segment)

        return segment.cells.compareAndSet(itemIndex(index), expectedValue, value)
    }

    fun compareAndSetTail(index: Long, expectedValue: Any?, value: Any?): Boolean {
        val currentTail = tail.get()
        val segment = findSegment(currentTail, id = segmentId(index))

        moveTailForward(segment)

        return segment.cells.compareAndSet(itemIndex(index), expectedValue, value)
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var position = start
        while (position.id < id) { // todo: it could happen that we skip some ids
            position = position.next.get() ?: createSegment(position)
        }

        return position
    }

    private fun moveTailForward(segment: Segment) = moveForward(segment, tail)
    private fun moveHeadForward(segment: Segment) = moveForward(segment, head)
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

    private fun segmentId(index: Long) = index / SEGMENT_SIZE

    private fun itemIndex(index: Long) = (index % SEGMENT_SIZE).toInt()

    private fun createSegment(parent: Segment): Segment {
        val newSegment = Segment(nextId.get())
        return if (parent.next.compareAndSet(null, newSegment)) {
            newSegment
        } else parent.next.get()!!
    }
}

class FAABasedQueue<E> : Queue<E> {
    private val infiniteArray = InfiniteArray() // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            // Increment the counter atomically via Fetch-and-Add.
//            val i = enqIdx.get()
//            enqIdx.set(i + 1)
            val i = enqIdx.getAndIncrement()

            // Atomically install the element into the cell if the cell is not poisoned.
//            infiniteArray.set(i.toInt(), element)
            if (infiniteArray.compareAndSetTail(i, null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
//        if (enqIdx.get() <= deqIdx.get()) return null // Obviously wrong condition (not that obviously actually)
//        if (deqIdx.get() >= enqIdx.get()) return null
            if (!shouldDequeue()) return null
            // Increment the counter atomically via Fetch-and-Add.
//            val i = deqIdx.get()
//            deqIdx.set(i + 1)
            val i = deqIdx.getAndIncrement()

            // Try to retrieve an element if the cell contains an element, poisoning the cell if it is empty.
//            return infiniteArray.get(i.toInt()) as E
            if (infiniteArray.compareAndSetHead(i, null, POISONED)) {
                continue
            }

            return infiniteArray.getAndSet(i, null) as? E
        }
    }

    private fun shouldDequeue(): Boolean {
        while (true) {
            val enqueueIdx = enqIdx.get()
            val dequeueIdx = deqIdx.get()
            if (enqIdx.get() != enqueueIdx) { continue }
            return dequeueIdx < enqueueIdx
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
