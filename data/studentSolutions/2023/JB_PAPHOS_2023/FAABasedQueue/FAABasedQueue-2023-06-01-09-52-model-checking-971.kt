package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val SEGM_SIZE = 2
    class dumby<E>(val arr: AtomicArray<Any?> = atomicArrayOfNulls(2),
                        val id: Int = 0) {
        val next = atomic<dumby<E>?>(null)
    }
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)
    private val head: AtomicRef<dumby<E>>
    private val tail: AtomicRef<dumby<E>>
    init {
        val dum = dumby<E>()
        head = atomic(dum)
        tail = atomic(dum)
    }

    fun getSegm(start: dumby<E>, id: Int): dumby<E> {
        val newDumby = dumby<E>(id = start.id + 1)
        if (id > start.id) {
            return if (start.next.compareAndSet(null, newDumby)) {
                tail.compareAndSet(start, newDumby)
                newDumby
            } else {
                tail.compareAndSet(start, start.next.value!!)
                start.next.value!!
            }
        }
        return start
    }
    fun getSegmBack(start: dumby<E>, id: Int): dumby<E> {
        if (id > start.id) {
            val startNext = start.next.value ?: dumby(id = start.id + 1)
            if (head.compareAndSet(start, startNext)) {
                return startNext
            }
        }
        return start
    }
    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            val curTail = tail.value
            val s = getSegm(curTail, i / SEGM_SIZE)
            if (s.arr[i % SEGM_SIZE].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val i = deqIdx.getAndIncrement()
            val curHead = head.value
            val s = getSegmBack(curHead, i / SEGM_SIZE)
            if (s.arr[i % SEGM_SIZE].compareAndSet(null, POISONED)) {
                continue
            }
            if (s.arr[i % SEGM_SIZE].value == POISONED) throw Exception("${i}${s.id}${enqIdx.value}${head.value.id}${tail.value.id}")
            return s.arr[i % SEGM_SIZE].value as E?
        }
    }
}

private val POISONED = Any()

