package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)
    private val a = atomic(InfiniteArray(2))

    class InfiniteArray {

        constructor(size: Int) {
            a = atomicArrayOfNulls<Any>(size)
        }
        operator fun get(i: Int): Any? = a[i].value

        operator fun set(i: Int, x: Any?) {
            a[i].value = x
        }

        fun size(): Int {
            return a.size
        }

        fun compareAndSet(i: Int, expected: Any?, newValue: Any?): Boolean {
            return a[i].compareAndSet(expected, newValue)
        }

        private val a: AtomicArray<Any?>
    }

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            if (i >= a.value.size()) {
                val newSize = 2 * a.value.size()
                val b = InfiniteArray(newSize)
                for (j in 0 until a.value.size()) {
                    b[j] = a.value[j]
                }
                a.value = b
            }
            if (a.value.compareAndSet(i, null, element)) {
                return
            }
        }
    }

    val RESTART = -1000000000
    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val i = deqIdx.getAndIncrement()
            if (a.value.compareAndSet(i, null, RESTART)) {
                continue
            }
            return a.value[i] as E
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

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

