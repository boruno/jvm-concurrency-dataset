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
            val oldNext = oldTail.next
            if (oldTail.add(element)) {
                if (tail.compareAndSet(oldTail, oldTail)) {
                    return
                }
            } else {
                if (oldNext == null) {
                    val newNode = Segment<E>()
                    if (!newNode.add(element)) {
                        println("you are dumb")
                    }
                    if (tail.compareAndSet(oldTail, newNode)) {
                        return
                    }
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
            if (isEmpty) {
                println("empty")
                return null
            }
            val oldHead = head.value
            val oldNext = oldHead.next
            val dequed = oldHead.deque()
            if (dequed != null) {
                if (head.compareAndSet(oldHead, oldHead)) {
                    return dequed
                }
            } else {
                if (oldNext == null) {
                    if (head.compareAndSet(oldHead, oldHead)) {
                        println("next null")
                        return null
                    }
                    continue
                }
                head.compareAndSet(oldHead, oldNext)
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
                val oldNext = oldHead.next
                if (oldHead.isEmpty()) {
                    if (oldNext == null) {
                        if (head.compareAndSet(oldHead, oldHead)) {
                            return true
                        }
                    }
                    head.compareAndSet(oldHead, oldNext!!)
                    continue
                }
                if (head.compareAndSet(oldHead, oldHead)) {
                    return false
                }
            }
        }
}

private class Segment<E> {
    var next: Segment<E>? = null
    val elements = atomicArrayOfNulls<Either<E>>(SEGMENT_SIZE)
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        for (i in 0 until SEGMENT_SIZE) {
            put(i, ValueType.BOTTOM)
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

    private fun put(i: Int, value: E) {
        elements[i].value = Either(ValueType.VALUE, value)
    }

    private fun put(i: Int, type: ValueType) {
        elements[i].value = Either(type, null)
    }

    private fun canPut(i: Int) = i < SEGMENT_SIZE && elements[i].value!!.type == ValueType.BOTTOM

    private fun canDeq(i: Int) = i < enqIdx.value && i < SEGMENT_SIZE && elements[i].value!!.type == ValueType.VALUE

    fun add(value: E): Boolean {
        val oldEnqIdx = enqIdx.getAndIncrement()
        if (canPut(oldEnqIdx)) {
            put(oldEnqIdx, value)
            return true
        }
        return false
    }

    fun deque(): E? {
        val oldDeqIdx = deqIdx.getAndIncrement()
        if (canDeq(oldDeqIdx)) {
            val value = elements[oldDeqIdx].value!!.value
            put(oldDeqIdx, ValueType.TOP)
            return value
        }
        return null
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

