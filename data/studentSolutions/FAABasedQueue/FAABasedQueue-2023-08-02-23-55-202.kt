//package day2

import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {

    private val head: AtomicRef<Node<Segment>>
    private val tail: AtomicRef<Node<Segment>>

    init {
        val dummy = Node(Segment(0))
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        while (true) {
            val elementIndex = enqIdx.getAndIncrement()
            val currentTail = tail.value
            val segment = findSegment(currentTail, elementIndex / SEGMENT_SIZE)
            // Try to move tail forward in case a new segment was created
            tail.compareAndSet(currentTail, segment)
            if (segment.element.cells[elementIndex % SEGMENT_SIZE].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (canBeEmpty()) return null

            val elementIndex = deqIdx.getAndIncrement()
            val currentHead = head.value
            val segmentNode = findSegment(currentHead, elementIndex / SEGMENT_SIZE)
            // Move head forward
            head.compareAndSet(currentHead, segmentNode)
            if (segmentNode.element.cells[elementIndex % SEGMENT_SIZE].compareAndSet(null, POISONED)) {
                continue
            }
            return segmentNode.element.cells[elementIndex % SEGMENT_SIZE].value as E
        }
    }

    private fun canBeEmpty(): Boolean {
        while (true) {
            val currentDeqIndex = deqIdx.value
            val currentEnqIndex = enqIdx.value
            // It's important to ensure that there was a time when `deqIdx` and `enqIdx`
            // had `currentDeqIndex` and `currentEnqIndex` values,
            // i.e. linearized history may have such state,
            // so we can safely compare these values
            if (currentDeqIndex != deqIdx.value) continue

            return currentDeqIndex >= currentEnqIndex
        }
    }

    private fun findSegment(start: Node<Segment>, segmentIndex: Int): Node<Segment> {
        var current = start
        while (true) {
            val currentSegment = current.element
            when {
                currentSegment.index < segmentIndex -> {
                    val nextNode = current.next.value
                    if (nextNode == null) {
                        val newNextNode = Node(Segment(currentSegment.index + 1))
                        current.next.compareAndSet(null, newNextNode)
                        // if previous CAS was successful, we set `newNextNode` which is not null
                        // Otherwise, another thread inserted not null value there.
                        // In both cases, current.next contains not null value here
                        current = current.next.value!!
                    }
                }
                currentSegment.index == segmentIndex -> return current
                else -> error("Unreachable")
            }
        }
    }
}

private val POISONED = Any()

private class Node<E>(
    val element: E
) {
    val next = atomic<Node<E>?>(null)
}

private const val SEGMENT_SIZE = 16

private class Segment(
    val index: Int
) {
    val cells: AtomicArray<Any?> = atomicArrayOfNulls(SEGMENT_SIZE)
}
