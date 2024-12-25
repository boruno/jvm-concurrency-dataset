//package mpp.faaqueue

import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
class FAAQueue<E> {
    private val head: AtomicRef<Segment>
    private val tail: AtomicRef<Segment>
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    fun enqueue(element: E) {
        while (true) {
            if (getSegment(tail.value, enqIdx.getAndAdd(1) / 2).cas((enqIdx.getAndAdd(1) % 2).toInt(), null, element)) {
                return
            }

            while (true) {
                if (tail.value.index < getSegment(tail.value, enqIdx.getAndAdd(1) / 2).index && tail.compareAndSet(tail.value, getSegment(tail.value, enqIdx.getAndAdd(1) / 2))) {
                        break
                } else {
                    break
                }
            }
        }
    }

    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }

            //val curHead: Segment =
            val index: Long = deqIdx.getAndAdd(1)
            val segment: Segment = getSegment(head.value, index / 2)

            while (true) {
                if (head.value.index < segment.index && head.compareAndSet(head.value, segment)) {
                       break
                } else {
                    break
                }
            }

            val indexInSegment: Int = (index % 2).toInt()

            if (segment.cas(indexInSegment, null, false)) {
                continue
            }

            return segment.get(indexInSegment) as E?
        }
    }

    val isEmpty: Boolean
        get() {
            return deqIdx.value == enqIdx.value
        }

    private fun getSegment(start: Segment, index: Long): Segment {
        var cur: Segment = start

        while (cur.index < index) {
            cur.next.compareAndSet(null, Segment(cur.index + 1))
            cur = cur.next.value!!
        }

        return cur
    }
}

private class Segment(val index: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(2)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
}