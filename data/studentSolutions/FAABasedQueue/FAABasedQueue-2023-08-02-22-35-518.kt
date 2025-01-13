//package day2

import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
        private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
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
            val s = findSegment1(start = curTail, id = i / SEGMENT_SIZE)
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
            val s = findSegment1(curHead, i / SEGMENT_SIZE)
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

    private fun findSegment1(start: Segment, id: Int): Segment {
        if (start.id == id) return start

        var cur: Segment = start
        while (true) {
            if (cur.id == id) return cur

            val cur1 = cur.next.value
            
            if (cur1 == null) {
//                while (true) {
                    val newSegment = Segment(id, SEGMENT_SIZE)
                    val currentTail = tail.value
                    if (currentTail.next.compareAndSet(null, newSegment)) {
                        tail.compareAndSet(currentTail, newSegment)
                        return newSegment
                    } else {
                        val s = currentTail.next.value!!
                        tail.compareAndSet(currentTail, s)
                        return s
                    }
//                }
            }
            
            cur = cur1
        }
    }

    private fun findSegment2(start: Segment, id: Int): Segment? {
        if (start.id == id) return start

        var cur: Segment = start
        while (true) {
            if (cur.id == id) return cur

            cur = cur.next.value ?: return null
        }
    }

    private fun moveTailForward(s: Segment) {
//        while (true) {
//            val currentTail = tail.value
//            if (currentTail == s) return
//            
//            if (currentTail.next.compareAndSet(null, s)) {
//                
//            }
//        }
    }

    private fun moveHeadForward(s: Segment) {

    }

    class Segment(val id: Int, size: Int) {
        val arr = atomicArrayOfNulls<Any>(size)
        val next = atomic<Segment?>(null)
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()

const val SEGMENT_SIZE = 256