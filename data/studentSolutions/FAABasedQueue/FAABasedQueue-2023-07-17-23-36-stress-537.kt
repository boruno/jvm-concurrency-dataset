package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val SEGM_SIZE = 1
    class dumby<E>(val arr: AtomicArray<Any?> = atomicArrayOfNulls(1),
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

    fun getSegm(id: Int): dumby<E> {
        var curStart = tail.value
        while(id > curStart.id) {
            val newDumby = dumby<E>(id = curStart.id + 1)
            if (curStart.next.compareAndSet(null, newDumby)) {
                tail.compareAndSet(curStart, newDumby)
                return newDumby
            }
            tail.compareAndSet(curStart, curStart.next.value!!)
            curStart = tail.value
        }
        return curStart
    }
    fun getSegmBack(id: Int): dumby<E>? {
        var curStart = head.value
        if (id < curStart.id) {
            return null
        }
        while(id > curStart.id) {
            val startNext = curStart.next.value ?: dumby(id=curStart.id + 1)
            if (head.compareAndSet(curStart, startNext)) {
                curStart.next.compareAndSet(null, startNext)
            }
            curStart = head.value
        }
        return curStart
    }
    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            val s = getSegm(i / SEGM_SIZE)
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
            val s = getSegmBack( i / SEGM_SIZE) ?: return null
            if (s.arr[i % SEGM_SIZE].compareAndSet(null, POISONED)) {
                continue
            }
            return s.arr[i % SEGM_SIZE].value as E
        }
    }
}

private val POISONED = Any()

