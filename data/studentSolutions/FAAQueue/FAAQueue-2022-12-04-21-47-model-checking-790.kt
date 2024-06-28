package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    // Указатель головы, аналогично очереди Майкла-Скотта (но первый узел _не_ дозорный)
    private val head: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    // Хвостовой указатель, аналогично очереди Майкла-Скотта
    private val tail: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment<E>(null)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     * Добавляет указанный элемент [x] в очередь.
     */
    fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement() //0 1
            // if (i > SEGMENT_SIZE - 1) {
            //     val cur_tail = tail.value
            //     val segment = Segment(element)
            //     if(cur_tail.next.compareAndSet(null, segment)) {
            //         enqIdx.compareAndSet(i + 1, 0)
            //         return
            //     }
            // } else {
            if (i == 1L) {
                val cur_tail = tail.value
                val segment = Segment(element)
                if(cur_tail.next.compareAndSet(null, segment)) {
                    enqIdx.compareAndSet(2L, 0L)
                    return
                }
            }
            val data = tail.value
            if (data.cas(i.toInt(),null, element)) {
                return
            }
            // }
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
            if (i == 0 ) {
                val cur_head = head.value
                val cur_head_next = cur_head.next
                if (head.compareAndSet(cur_head, cur_head_next.value!!)) {
                    deqIdx.compareAndSet(-1, (SEGMENT_SIZE - 1).toLong())
                    return cur_head_next.value!!.elements[i].value as E?
                }
            } else {
                val el = tail.value.elements[i].value
                if (tail.value.elements[i].compareAndSet(el, BROKEN)) {
                    continue
                }
                if (el == null) {
                    continue
                }
                return el as E
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     * Возвращает `true`, если эта очередь пуста, или `false` в противном случае.
     */
    val isEmpty: Boolean
        get() {
            return head.value.next.value == null
        }

    companion object {
        private val BROKEN = Any()
    }
}

private class Segment<E>(e : E?) {
    val next = atomic<Segment<E>?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    init {
        put(0, e)
    }

    private fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

