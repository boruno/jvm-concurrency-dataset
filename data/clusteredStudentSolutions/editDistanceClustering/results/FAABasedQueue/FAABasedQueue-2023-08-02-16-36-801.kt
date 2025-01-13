//package day2

import kotlinx.atomicfu.*

private val SEGMENT_SIZE = 1024

class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicRef<Segment<E>?> = atomic(null)
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    // Helper class representing a segment of the linked list
    private class Segment<E> {
        val array: AtomicArray<E?> = atomicArrayOfNulls(SEGMENT_SIZE)
    }

    init {
        // Initialize the first segment when the queue is created
        val initialSegment = Segment<E>()
        head.value = initialSegment
    }

    override fun enqueue(element: E) {
        while (true) {
            val currentEnqIdx = enqIdx.value
            val currentSegment = head.value!!
            val offset = currentEnqIdx % SEGMENT_SIZE

            if (offset == 0) {  // Create a new segment if the current one is full
                val newSegment = Segment<E>()
                newSegment.array[0].value = element

                if (head.compareAndSet(currentSegment, newSegment)) {  // Try to advance the head to the new segment
                    enqIdx.incrementAndGet()
                    return
                }
            } else {
                if (currentSegment.array[offset].compareAndSet(null, element)) {  // Enqueue the element in the current segment
                    enqIdx.incrementAndGet()
                    return
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            val currentDeqIdx = deqIdx.value
            val currentEnqIdx = enqIdx.value

            if (currentDeqIdx >= currentEnqIdx) {  // The queue is empty
                return null
            }

            val currentSegment = head.value!!
            val offset = currentDeqIdx % SEGMENT_SIZE
            val nextSegment = if (offset == SEGMENT_SIZE - 1) {  // Move to the next segment if the current one is empty
                val newSegment = Segment<E>()
                newSegment
            } else {
                currentSegment
            }

            val element = currentSegment.array[offset].getAndSet(null)  // Dequeue the element from the current segment
            if (element != null) {
                deqIdx.incrementAndGet()
                return element
            }

            head.compareAndSet(currentSegment, nextSegment)  // Try to advance the head to the next segment
        }
    }
}
