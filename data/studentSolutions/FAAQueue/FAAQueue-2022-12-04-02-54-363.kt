//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    // Указатель головы, аналогично очереди Майкла-Скотта (но первый узел _не_ дозорный)
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    // Хвостовой указатель, аналогично очереди Майкла-Скотта
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     * Добавляет указанный элемент [x] в очередь.
     */
    fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement().toInt()
            if(tail.value.elements[i].compareAndSet(null, element)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     * Извлекает первый элемент из очереди и возвращает его;
       возвращает `null`, если очередь пуста.
     */
    @Suppress("UNCHECKED_CAST")
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) {
                return null
            }
            val i = deqIdx.getAndIncrement().toInt()
            val el = tail.value.elements[i].value
            if (tail.value.elements[i].compareAndSet(el, BROKEN)) {
                continue
            }
            if (el == null) {
                continue
            }
            return el as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     * Возвращает `true`, если эта очередь пуста, или `false` в противном случае.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value <= deqIdx.value
        }

    companion object {
        private val BROKEN = Any()
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

