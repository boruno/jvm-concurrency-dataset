//package day2

import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified` and implement the infinite array on a linked list of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)
    private val head: AtomicReference<Segment>
    private val tail: AtomicReference<Segment>

    init {
        val segment = Segment(0)
        head = AtomicReference(segment)
        tail = AtomicReference(segment)
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var previous: Segment? = null
        var current: Segment? = start
        while (current != null) {
            if (current.id == id)
                return current
            previous = current
            current = current.next.get()
        }
        val newSegment = Segment(previous!!.id + 1)
        assert(newSegment.id == id) //note(vg): it supposed to be impossible that we need to add several segments at once
        previous.next.compareAndSet(null, newSegment) //note(vg): it's ok to fail here, it means that somebody had already created a new segment for us
        return previous.next.get()!! //note(vg): do not use newSegment here!
    }

    private fun moveHeadForward(s: Segment){
        while (true) {
            val curHead = head.get()
            if (s.id <= curHead.id)
                return
            if (head.compareAndSet(curHead, s))
                return
        }
//        val curHead = head.get()
//        if (s.id > curHead.id)
//            head.compareAndSet(curHead, s)
    }

    private fun moveTailForward(s: Segment){
        while (true) {
            val curTail = tail.get()
            if (s.id <= curTail.id)
                return
            if (tail.compareAndSet(curTail, s))
                return
        }
//        val curTail = tail.get()
//        if (s.id > curTail.id)
//            tail.compareAndSet(curTail, s)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get()
            val i = enqIdx.getAndIncrement()
            val s: Segment = findSegment(curTail, i / SEGMENT_SIZE)
            moveTailForward(s)
            if (s.cells.compareAndSet(i.toInt() % SEGMENT_SIZE, null, element))
                return
        }
    }

    private fun shouldTryToDequeue(): Boolean {
        while (true) {
            val curEnqIdx = enqIdx.get()
            val curDeqIdx = deqIdx.get()
            if (curEnqIdx != enqIdx.get()) continue
            return curDeqIdx < curEnqIdx
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldTryToDequeue())
                return null
            val curHead = head.get()
            val i = deqIdx.getAndIncrement()
            val s: Segment = findSegment(curHead, i / SEGMENT_SIZE)
            moveHeadForward(s)
            if (!s.cells.compareAndSet(i.toInt() % SEGMENT_SIZE, null, POISONED)) {
                val result = s.cells.get(i.toInt() % SEGMENT_SIZE) as E
                s.cells.set(i.toInt() % SEGMENT_SIZE, null)
                return result
            }
        }
    }
}

private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2

// TODO: poison cells with this value.
private val POISONED = Any()