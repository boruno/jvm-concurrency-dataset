package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var cur = start
        while (true) {
            if (cur.id == id) {
                return cur
            }
            if (cur.next != null) {
                cur = cur.next!!
            } else {
                break
            }
        }
        return Segment(id)
    }

    private fun moveTailForward(s: Segment) {
        val curTail = tail.value
        if (curTail.id != s.id) {
            tail.compareAndSet(curTail, s)
        }
    }

    private fun moveHeadForward(s: Segment) {
        val curHead = head.value
        if (curHead.id != s.id) {
            head.compareAndSet(curHead, s)
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val ix = enqIdx.incrementAndGet()
            val s = findSegment(curTail, ix / SEGMENT_SIZE)
            moveTailForward(s)
            if(s.cas((ix % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if(deqIdx.value <= enqIdx.value){
                return null
            }
            val curHead = head.value
            val ix = enqIdx.incrementAndGet()
            val s = findSegment(curHead, ix / SEGMENT_SIZE)
            moveHeadForward(s)
            if(s.cas((ix % SEGMENT_SIZE).toInt(), null, BOTTOM)) {
                continue
            }
            return s[(ix % SEGMENT_SIZE).toInt()] as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            TODO("implement me")
        }
}

private class Segment(val id: Long) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    operator fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
const val BOTTOM = "b9a3a9e3da08a43b91eb226063e1e8f82bd9457904d2cb841e86d0e4d35bb799"
