package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicRef<Segment>
    private val tail: AtomicRef<Segment>
    private val numberOfSegments: AtomicInt
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)
    companion object { const val segmentSize = 16 }

    init {
        numberOfSegments = atomic(0)
        val initSegment = Segment(numberOfSegments.getAndIncrement())
        head = atomic(initSegment)
        tail = atomic(initSegment)
    }

    override fun enqueue(element: E) {
        // TODO("Implement me!")
        while (true) {
            val curTail = tail
            val i = enqIdx.getAndIncrement()
            val arrI = i / segmentSize
            val s = findSegment(arrI)
            moveTailForward(s)
            if (s.arr[arrI].compareAndSet(null, element)) { return }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // TODO("Implement me!")
        while (true) {
            // if (enqIdx.value <= deqIdx.value) { return null }
            val enqI = enqIdx.value
            val deqI = deqIdx.value
            if (enqI != enqIdx.value) continue
            if (enqI <= deqI) { return null }
            // try to dequeue
            val curHead = head
            val i = deqIdx.getAndIncrement()
            val arrI = i / segmentSize
            val s = findSegment(arrI)
            moveHeadForward(s)
            if (s.arr[arrI].compareAndSet(null, POISONED)) { continue }
            return s.arr[arrI].value as E
        }
    }

    private fun findSegment(id : Int) : Segment {
        var i = 0
        var it = head.value
        while (i < id) {
            if (it.next.value == null) {
                var newSegment = Segment(numberOfSegments.getAndIncrement())
                if (it.next.compareAndSet(null, newSegment)) {
                    continue
                } else { continue }
            } else {
                it = it.next.value!!
                i++
            }
        }
        return it
    }

    private fun moveTailForward(s : Segment) : Unit {
        while (true) {
            val curTail = tail.value
            val currentNOS = curTail.id
            if (currentNOS < s.id) {
                if (tail.compareAndSet(curTail, s)) { return }
            } else { return }
        }
    }
    private fun moveHeadForward(s : Segment) : Unit {
        while (true) {
            val curHead = head.value
            val curHeadID = curHead.id
            if (curHeadID < s.id) {
                if (head.compareAndSet(curHead, s)) { return }
            } else { return }
        }
    }

    private class Segment(val id : Int) {
        val arr = atomicArrayOfNulls<Any?>(segmentSize)
        val next = atomic<Segment?>(null)
    }
}

private val POISONED = Any()