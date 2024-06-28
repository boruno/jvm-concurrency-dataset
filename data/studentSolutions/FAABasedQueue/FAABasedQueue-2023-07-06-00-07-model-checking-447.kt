package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    private val head: AtomicRef<Segment>
    private val tail: AtomicRef<Segment>

    init {
        val dummy = Segment(0)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()

            val curTail = tail.value
            val segment = findSegment(curTail, i / SEGMENT_SIZE)

            if (segment.array[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (shouldNotTryDeque()) {
                return null
            }

            val i = deqIdx.getAndIncrement()
            val segment = findSegment(head.value, i / SEGMENT_SIZE)
            val si = (i % SEGMENT_SIZE).toInt()

            if (segment.array[si].compareAndSet(null, POISONED)) {
                tryDequeueSegment(segment)
                continue
            }

            return segment.array[si].value as E
        }
    }

    private fun shouldNotTryDeque(): Boolean {
        while (true) {
            val curDeq = deqIdx.value
            val curEnq = enqIdx.value

            @Suppress("KotlinConstantConditions")
            if (curDeq == deqIdx.value) {
                return curDeq >= curEnq
            }
        }
    }

    private fun findSegment(start: Segment, index: Long): Segment {
        var node = start
        while (node.index != index) {
            node = node.next.value ?: break
        }

        if (node.index == index) {
            return node
        }

        while (true) {
            val segment = tryEnqueueSegment()
            if (segment.index == index) {
                return segment
            }
        }
    }

    private fun tryEnqueueSegment(): Segment {
        val curTail = tail.value
        val node = Segment(curTail.index + 1)
        return if (curTail.next.compareAndSet(null, node)) {
            node
        } else {
            curTail.next.value!!
        }
    }

    private fun tryDequeueSegment(segment: Segment) {
        val done = segment.processed.getAndIncrement()

        val curHead = head.value
        val next = curHead.next
        val value = next.value ?: return

        if (value.index == segment.index && done == SEGMENT_SIZE) {
            head.compareAndSet(curHead, value)
        }
    }

    class Segment(val index: Long) {
        val array: AtomicArray<Any?> = atomicArrayOfNulls(SEGMENT_SIZE)
        val next = atomic<Segment?>(null)
        val processed = atomic(0)
    }

    companion object {
        private val POISONED = Any()

        private const val SEGMENT_SIZE: Int = 1
    }
}
