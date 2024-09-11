package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        firstNode.setId(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    enum class Operation {
        enque,
        deque
    }


    private fun findSegment(start: Segment, index: Int, operation: Operation): Segment { // head | tail
        if (operation == Operation.enque) {
            var curTail = start
            while (true) {
                if (curTail.getId() >= index) {
                    for (i in 0 until SEGMENT_SIZE) {
                        if (curTail.elements[i].value == null) {
                            return curTail
                        }
                    }
                    val newTail = Segment()
                    newTail.setId(curTail.getId() + 1)
                    return newTail
                }
                if (curTail.next.value != null) {
                    curTail = curTail.next.value!!
                }
                else
                {
                    val newTail = Segment()
                    newTail.setId(curTail.getId() + 1)
                    curTail.next.value = newTail
                }
            }
        }
        if (operation == Operation.deque) {
            var curHead = start
            while (true) {
                if (curHead.getId() == index) {
                    return curHead
                }
                if (curHead.next.value != null) {
                    curHead = curHead.next.value!!
                }
                else
                {
                    val newSegment = Segment()
                    newSegment.setId(0)
                    return newSegment
                }
            }
        }
        return Segment()
    }


    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val index = enqIdx.getAndIncrement().toInt()
            val segment = findSegment(curTail,index / SEGMENT_SIZE, Operation.enque)
            if (curTail.getId() > segment.getId()) continue
            else if (curTail.getId() < segment.getId()) tail.value = segment
            if (tail.value.elements[index % SEGMENT_SIZE].compareAndSet(null, element))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) return null
            val curHead = head.value
            val index = deqIdx.getAndIncrement().toInt()
            val segment = findSegment(curHead, index / SEGMENT_SIZE, Operation.deque)
            if (curHead.getId() > segment.getId()) continue
            else if (curHead.getId() < segment.getId()) head.value = segment
            if (head.value.elements[index % SEGMENT_SIZE].compareAndSet(null, "⟂"))
                continue
            val ret = segment.elements[index % SEGMENT_SIZE].value as E?
            return ret
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() { return deqIdx.value >= enqIdx.value }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    private var _id = 0

    fun getId(): Int {
        return _id
    }
    fun setId(id: Int) {
        _id = id
    }


    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

