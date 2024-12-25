//package mpp.faaqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
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
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndAdd(1)
            var s = curTail
            while (s.id != i / SEGMENT_SIZE) {
                val tmp = s.next.value
                if (tmp != null) {
                    s = tmp
                } else {
                    val newSeg = Segment()
                    newSeg.id = s.id + 1
                    tail.value.next.compareAndSet(s.next.value, newSeg)
                }
            }
            if (s.id >= tail.value.id) {
                while (!tail.compareAndSet(curTail, s)) {

                }
            }
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
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
            if (isEmpty) {
                return null
            }
            val curHead = head.value
            val i = deqIdx.getAndAdd(1)
            var s = curHead
            while (s.id != i / SEGMENT_SIZE) {
                val tmp = s.next.value
                if (tmp != null) {
                    s = tmp
                } else {
                    val newSeg = Segment()
                    newSeg.id = s.id + 1
                    head.value.next.compareAndSet(s.next.value, newSeg)
                }
            }
            if (s.id >= head.value.id) {
                while (!head.compareAndSet(curHead, s)) {

                }
            }
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, "BROKEN")) {
                continue
            }
            return s.elements[i.toInt()].value as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value <= deqIdx.value;
        }
}

private class Segment {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    var id: Long = 0

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

// moveTail
// moveHead глобальный  if s.id >= head.id head.CAS(s), аналогично с tail/ Если id меньше то чилю
// moveHead\tail CAS в цикле ; Сравнение с id идет с глобальным tail