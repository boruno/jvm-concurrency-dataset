package day2

import day1.*
import kotlinx.atomicfu.*

private const val SEGMENT_SIZE = 2
// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    private var head = Segment(0)
    private var tail = head

    private class Segment(val index: Int) {
        val array = atomicArrayOfNulls<Any?>(SEGMENT_SIZE)
        val next = atomic<Segment?>(null)

        fun getOrCreateNextSegment(): Segment {
            next.compareAndSet(null, Segment(index + 1))
            return next.value!!
        }
    }

    private fun findSegment(segmentIndex: Int, start: Segment): Segment {
        var current = start
        repeat(segmentIndex - start.index) {
            current = current.getOrCreateNextSegment()
        }

        return current
    }


    override fun enqueue(element: E) {
        while (true) {
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = enqIdx.getAndIncrement()

            val segmentIndex = i / SEGMENT_SIZE
            val segment = findSegment(segmentIndex, start = tail)

            // move tail forward
            if (segment.index > tail.index) {
                tail = segment
            }

            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            val elementIndex = i % SEGMENT_SIZE
            if (segment.array[elementIndex].compareAndSet(null, element)) {
                return
            } else {
                continue // it's poisoned
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while(true) {
            // Is this queue empty?
            if (!shouldTryDeq()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.getAndIncrement()
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.

            val segmentIndex = i / SEGMENT_SIZE
            val segment = findSegment(segmentIndex, start = head)
            // move head forward
            if (segment.index > head.index) {
                head = segment
            }

            val elementIndex = i % SEGMENT_SIZE
            val element = segment.array[elementIndex]
            if (element.compareAndSet(null, POISONED)) {
                continue
            } else {
                return element.value as E
            }
        }

    }

    private fun shouldTryDeq(): Boolean {
        while (true) {
            val currentDeqIndex = deqIdx.value
            val currentEnqIndex = enqIdx.value

            @Suppress("KotlinConstantConditions")
            if (currentDeqIndex == deqIdx.value) {
                // we got a snapshot
                return currentDeqIndex < currentEnqIndex
            }
        }
    }
}
private val POISONED = Any()
