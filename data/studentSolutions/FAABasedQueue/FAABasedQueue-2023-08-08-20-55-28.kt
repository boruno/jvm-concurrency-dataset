//package day2

import MSQueue
import Queue
import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {

    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)
    private val head: AtomicReference<Segment>
    private val tail: AtomicReference<Segment>

    init {
        val dummy = Segment(-1)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get()
            val enqIndex = enqIdx.getAndIncrement()
            val s = findSegment(curTail, enqIndex / SEGMENT_SIZE)
            if (s.cells.compareAndSet((enqIndex % SEGMENT_SIZE).toInt(), null, element)) return
        }
    }

    private fun findSegment(from: Segment, index: Long): Segment {
        var node = from
        while (true) {
            val curTail = tail.get()
            val next = node.next
            val newSegment = Segment(index)
            if (next.compareAndSet(null, newSegment)) {
                tail.compareAndSet(curTail, newSegment)
                return newSegment
            } else {
                if (next.get()!!.id == index) return next.get()!!
            }
            node = next.get()!!
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            val curHead = head.get()
            if (deqIdx.get() >= enqIdx.get()) return null
            val deqIndex = deqIdx.getAndIncrement()
            val s = findSegment(curHead, deqIndex)
            val indexByModulo = (deqIndex % SEGMENT_SIZE).toInt()
            if (s.cells.compareAndSet(indexByModulo, null, POISONED)) continue
            val res = s.cells.get(indexByModulo) as E
            s.cells.set(indexByModulo, null)
            return res
        }
    }
}

private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2
private val POISONED = Any()
