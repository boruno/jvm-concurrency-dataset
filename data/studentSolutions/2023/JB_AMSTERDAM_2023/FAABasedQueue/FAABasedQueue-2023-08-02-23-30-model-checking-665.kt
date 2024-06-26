package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    val head: AtomicRef<Segment> 
    val tail: AtomicRef<Segment>

    init {
        val dummy = Segment(-1, 0)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = enqIdx.getAndIncrement()
            val s = findSegment(start = curTail, id = i / SEGMENT_SIZE.toLong())
            moveTailForward(s)
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            val offset = i % SEGMENT_SIZE
            val ref = s.arr[offset]
            if (ref.compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            // Is this queue empty?
//            if (enqIdx.value <= deqIdx.value) return null //???
//            if (deqIdx.value >= enqIdx.value) return null //???
            if (isEmpty()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.getAndIncrement()
            val s = findSegment(curHead, i / SEGMENT_SIZE.toLong())
            moveHeadForward(s)
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            val offset = i % SEGMENT_SIZE
            val ref = s.arr[offset]
            if (ref.compareAndSet(null, POISONED)) continue

            return ref.value as E 
        }
    }

    private fun isEmpty(): Boolean {
        while (true) {
            val curDeqIdx = deqIdx.value
            val curEnqIdx = enqIdx.value
            if (curDeqIdx != deqIdx.value) continue
            return curDeqIdx >= curEnqIdx
        }
    }

    private fun findSegment(start: Segment, id: ID): Segment {
        if (start.id == id) return start

        var curSegment: Segment = start
        while (true) {
            if (curSegment.id == id) return curSegment

            val next = curSegment.next
            val nextSegment = next.value
            
            if (nextSegment == null) {
                val newSegment = Segment(id, SEGMENT_SIZE)
                if (next.compareAndSet(null, newSegment)) {
                   // tail.compareAndSet(curSegment, newSegment)
                    return newSegment
                } else {
                    // tail.compareAndSet(curSegment, curSegment.next.value!!)
                    continue
                }
            }
            
            curSegment = nextSegment
        }
    }

    private fun moveTailForward(s: Segment) {
        while (true) { //TODO do we really need a loop here?
            val curTail = tail.value
            if (curTail.id >= s.id) return
            if (tail.compareAndSet(curTail, s)) return
        }
    }

    private fun moveHeadForward(s: Segment) {
        while (true) { //TODO do we really need a loop here?
            val curHead = head.value
            if (curHead.id >= s.id) return
            if (head.compareAndSet(curHead, s)) return
        }
    }

    class Segment(val id: ID, size: Int) {
        val arr = atomicArrayOfNulls<Any>(size)
        val next = atomic<Segment?>(null)
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()

const val SEGMENT_SIZE = 2 // TODO how to chose size?

typealias ID = Long