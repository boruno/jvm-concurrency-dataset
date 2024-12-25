//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    // Указатель головы, аналогично очереди Майкла-Скотта (но первый узел _не_ дозорный)
    private val head: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    // Хвостовой указатель, аналогично очереди Майкла-Скотта
    private val tail: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

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
            if (i > SEGMENT_SIZE - 1) {
                val cur_tail = tail.value
                val segment = Segment(element)
                enqIdx.compareAndSet(i + 1, 0)
                if (cur_tail.next.compareAndSet(null, segment)) {
                    tail.compareAndSet(cur_tail, segment)
                    return
                } else {
                    tail.compareAndSet(cur_tail, cur_tail.next.value!!)
                }
            } else {
                val data = tail.value
                if (data.cas(i,null, element)) {
                    return
                }
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
            val i = deqIdx.getAndIncrement()
            if (i > SEGMENT_SIZE - 1) {
                val cur_head = head.value
                val cur_head_next = cur_head.next
                deqIdx.compareAndSet(i + 1, 0)
                if (head.compareAndSet(cur_head, cur_head_next.value!!)) {
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
            val cur_head = head.value
            if (cur_head.next.value == null) {
                return true
            }

            if (enqIdx.value < deqIdx.value || deqIdx.value > SEGMENT_SIZE) {
                return true
            }

            return false
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

