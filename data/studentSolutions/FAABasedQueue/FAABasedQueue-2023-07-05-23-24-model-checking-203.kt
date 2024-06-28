package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.NoSuchElementException

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
const val SEGMENT_SIZE = 10

class FAABasedQueue<E> : Queue<E> {
    val enqIdx = atomic(0L)
    val deqIdx = atomic(0L)
    val segments = MSQueue<Segment>()

    init {
        segments.enqueue(Segment(0L))
    }

    class Segment(val idx: Long) {
        val data = atomicArrayOfNulls<Any?>(SEGMENT_SIZE)

    }

    override fun enqueue(element: E) {
        while (true) {
            val idx = enqIdx.getAndIncrement()
            if (segments.getSegment(
                    idx / SEGMENT_SIZE
                ).data[(idx % SEGMENT_SIZE).toInt()].compareAndSet(null, element)
            ) return
        }
    }


    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!canTryObtain()) return null
            val idx = deqIdx.getAndIncrement()
            if (segments.getSegment(
                    idx / SEGMENT_SIZE
                ).data[(idx % SEGMENT_SIZE).toInt()].compareAndSet(null, POISONED)
            ) continue
            return segments.getSegment(idx / SEGMENT_SIZE).data[(idx % SEGMENT_SIZE).toInt()].value as E
        }
    }

    @Suppress("KotlinConstantConditions")
    private fun canTryObtain(): Boolean {
        while (true) {
            val curDeqIdx = deqIdx.value
            val curEnqIdx = enqIdx.value
            if (curDeqIdx != deqIdx.value) continue
            return curDeqIdx < curEnqIdx
        }
    }

    private fun MSQueue<Segment>.getSegment(idx: Long): Segment {
        if (tail.value.element!!.idx == idx) return tail.value.element!!
        else if (tail.value.element!!.idx > idx) {
            var curidx: Long? = head.value.element?.idx
            var curseg: Segment? = null
            while (curidx != null && curidx != idx) {
                curidx = head.value.next.value?.element?.idx
                curseg = head.value.next.value?.element
            }
            if (curidx != null) return curseg!!
            throw NoSuchElementException()
        } else {
            var curidx = tail.value.element!!.idx
            while (curidx != idx) {
                val tmp = curidx + 1
                val seg = Segment(tmp)
                segments.enqueue(seg)
                curidx = tmp
                if (tmp==idx) return seg
            }
        }
        throw IllegalStateException()
    }
}

