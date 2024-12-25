//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {

    private val BROKEN = Any()

    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun moveTail(id: Long) {
        while (true) {
            val curTail = tail.value
            if (curTail.id >= id)
                break
            val newTail = Segment(curTail.id + 1)
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
            } else {
                curTail.next.value?.let{
                    tail.compareAndSet(curTail, it)
                }
            }
        }
    }

    private fun findFromTail(prevTail: Segment, id: Long): Segment {
        if (prevTail.id < id)
            moveTail(id)
        var result = prevTail
        while (result.id < id) {
            result = result.next.value!!
        }
        return result
    }


    private fun findFromHead(prevHead: Segment, id: Long): Segment? {
        var result = prevHead
        while (result.id < id) {
            result = result.next.value?:return null
        }
        while (true) {
            val curHead = head.value
            if (curHead.id >= result.id || head.compareAndSet(curHead, result)) {
                break
            }
        }
        return result
    }

    /**
    * Adds the specified element [x] to the queue.
    */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findFromTail(curTail, i / SEGMENT_SIZE)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, element))
                return
        }
    }

    /**
    * Retrieves the first element from the queue and returns it;
    * returns `null` if the queue is empty.
    */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val s = findFromHead(curHead, i / SEGMENT_SIZE)?:continue
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, BROKEN))
                continue
            return s.get((i % SEGMENT_SIZE).toInt()) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() = (enqIdx.value <= deqIdx.value)
}


private class Segment(val id: Long = 0) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

