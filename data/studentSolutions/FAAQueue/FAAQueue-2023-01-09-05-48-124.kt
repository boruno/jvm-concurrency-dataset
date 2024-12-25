//package mpp.faaqueue

import kotlinx.atomicfu.*

enum class ValueType {
    BOTTOM,
    TOP,
    VALUE
}

class Either<E>(val type: ValueType, val value: E?)

class FAAQueue<E> {
    private val head: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue

    init {
        val firstNode = Segment<E>()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val oldTail = tail.value
            val oldNext = oldTail.next.value
            if (oldTail.add(element)) {
                return
            } else {
                if (oldNext == null) {
                    tail.value.next.compareAndSet(null, Segment<E>())
                } else {
                    tail.compareAndSet(oldTail, oldNext)
                }
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val oldHead = head.value
            val oldNext = oldHead.next.value
            val dequed = oldHead.deque()
            if (dequed != null) {
                return dequed
            } else {
                if (oldNext == null) {
                    if (tail.value.next.compareAndSet(null, null)) {
                        return null
                    }
                }
                head.compareAndSet(oldHead, oldNext!!)
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val oldHead = head.value
                val oldNext = oldHead.next.value
                if (oldHead.isEmpty()) {
                    if (oldNext == null) {
                        if (head.compareAndSet(oldHead, oldHead)) {
                            return true
                        }
                    }
                    head.value = oldNext!!
                    continue
                }
                return false
            }
        }
}

private class Segment<E>() {
    val next: AtomicRef<Segment<E>?> = atomic(null)
    val elements = atomicArrayOfNulls<Either<E>>(SEGMENT_SIZE)
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        for (i in 0 until SEGMENT_SIZE) {
            elements[i].value = Either<E>(ValueType.BOTTOM, null)
        }
    }

    fun isEmpty(): Boolean {
        while (true) {
            val oldDeqIdx = deqIdx.value
            val oldEnqIdx = enqIdx.value
            if (oldDeqIdx >= SEGMENT_SIZE || oldDeqIdx >= oldEnqIdx) {
                if (enqIdx.compareAndSet(oldEnqIdx, oldEnqIdx)) {
                    return true
                }
                continue
            }
            return false
        }
    }

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Either<E>?, update: Either<E>?) = elements[i].compareAndSet(expect, update)

    fun add(value: E): Boolean {
        val oldEnqIdx = enqIdx.getAndIncrement()
        if (oldEnqIdx >= SEGMENT_SIZE) {
            return false
        }
        val oldElem = elements[oldEnqIdx].value!!
        if (oldElem.type != ValueType.BOTTOM) {
            return false
        }
        if (cas(oldEnqIdx, oldElem, Either(ValueType.VALUE, value))) {
            return true
        }
        return false
    }

    fun deque(): E? {
        var oldDeqIdx = deqIdx.getAndIncrement()
        while (oldDeqIdx < SEGMENT_SIZE) {
            val was = elements[oldDeqIdx].getAndSet(Either(ValueType.TOP, null))
            if (was!!.type == ValueType.VALUE) {
                return was.value
            }
            oldDeqIdx = deqIdx.getAndIncrement()
        }
        return null
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

