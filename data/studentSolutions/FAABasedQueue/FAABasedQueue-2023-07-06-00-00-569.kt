//package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    val enqId = atomic(0)
    val deqId = atomic(0)
    val dummy = Segment<E>(0)
    val head = atomic(dummy)
    val tail = atomic(dummy)

    private fun findSegment(index: Int): Segment<E> {
        val segmentIndex = index / size

        while (true) {
            var current = head.value
            val localTail = tail.value
            while (true) {
                if (current.index == segmentIndex)
                    return current

                if (current == localTail)
                    break

                current = current.next.value ?: break
            }

            if (localTail.next.compareAndSet(null, Segment(localTail.index + 1))) {
                tail.compareAndSet(localTail, localTail.next.value!!)
            } else {
                tail.compareAndSet(localTail, localTail.next.value!!)
            }
        }


    }

    override fun enqueue(element: E) {
        while (true) {
            val curEncId = enqId.getAndIncrement()
            val segment = findSegment(curEncId)
            if (segment.array[curEncId % size].compareAndSet(null, element))
                return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (deqId.value >= enqId.value)
                return null

            val curDeqId = deqId.getAndIncrement()
            val segment = findSegment(curDeqId)
            if (segment.array[curDeqId % size].compareAndSet(null, POISONED))
                continue

            return segment.array[curDeqId].value as E
        }
    }

    companion object {
        const val size = 4
    }

    class Segment<E>(val index: Int) {
        val array = atomicArrayOfNulls<Any>(size)
        val next = atomic<Segment<E>?>(null)
    }
}

private val POISONED = Any()