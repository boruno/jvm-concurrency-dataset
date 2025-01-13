//package day2

import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)
    private val head: AtomicRef<Segment>
    private val tail: AtomicRef<Segment>

    init {
        val dummy = Segment()
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        var curSegment = tail.value
        fun findSegment(id: Int) {
            if (curSegment.element[id].value == null) return
            else {
                val newNode = Segment()
                if (curSegment.next.compareAndSet(null, newNode)) curSegment = newNode
                else {
                    curSegment = curSegment.next.value!!
                    findSegment(id)
                }
            }
        }

        val curTail = curSegment
        val i = enqIdx.getAndIncrement()
        val id = i % SEGMENT_SIZE
        findSegment(id)
        if (curSegment != curTail) tail.compareAndSet(curTail, curSegment)
        if (curSegment.element[id].compareAndSet(null, element)) return
        enqueue(element)
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        if (deqIdx.value >= enqIdx.value) return null
        var curSegment = head.value
        fun findSegment(id: Int) {
            val el = curSegment.element[id].value
            if (el != null && el != POISONED) return
            else {
                val newNode = Segment()
                if (curSegment.next.compareAndSet(null, newNode)) curSegment = newNode
                else {
                    curSegment = curSegment.next.value!!
                    findSegment(id)
                }
            }
        }

        val curHead = curSegment
        val i = deqIdx.getAndIncrement()
        val id = i % SEGMENT_SIZE
        findSegment(id)
        if (curSegment != curHead) head.compareAndSet(curHead, curSegment)
        return if (curSegment.element[id].compareAndSet(null, POISONED)) {
            dequeue()
        } else {
            curSegment.element[id].value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()

private val SEGMENT_SIZE = 32

class Segment(
    val element: AtomicArray<Any?> = atomicArrayOfNulls<Any?>(SEGMENT_SIZE)
) {
    val next = atomic<Segment?>(null)
}