//package day2

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

    private fun getSegm(id: Int): dumby<E> {
        var curStart = tail.value
        while(id > curStart.id) {
            val newDumby = dumby<E>(id = curStart.id + 1)
            if (curStart.next.compareAndSet(null, newDumby)) {
                return newDumby
            }
            curStart = curStart.next.value!!
        }
        return curStart
    }
    private fun getSegmBack(id: Int): dumby<E> {
        var curStart = head.value
        while(id > curStart.id) {
            val startNext = curStart.next.value ?: dumby(id=curStart.id + 1)
            if (curStart.next.compareAndSet(null, startNext)) {
                return startNext
            }
            curStart = startNext
        }
        return curStart
    }

    private fun moveHead() {
        while(true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value ?: dumby(id=curHead.id + 1)
            if (head.compareAndSet(curHead, curHeadNext)) {
                return
            }
        }
    }

    private fun moveTail() {
        while(true) {
            val curTail = tail.value
            val node = dumby<E>(id=curTail.id + 1)
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                return
            }
            tail.compareAndSet(curTail, curTail.next.value!!)
        }
    }
    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            val s = getSegm(i / SEGM_SIZE)
            moveTail()
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
            val s = getSegmBack( i / SEGM_SIZE)
            moveHead()
            if (s.arr[i % SEGM_SIZE].compareAndSet(null, POISONED)) {
                continue
            }
            return s.arr[i % SEGM_SIZE].value as E
        }
    }
}

private val POISONED = Any()

