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

    private fun getSegm(start: dumby<E>, id: Int): dumby<E> {
        var curStart = start
        while(id > curStart.id) {
            val next = curStart.next.value ?: dumby(id = curStart.id + 1)
            if (curStart.next.compareAndSet(null, next)) {
                return curStart.next.value!!
            }
            curStart = curStart.next.value!!
        }
        return curStart
    }

    private fun moveHead(id: Int) {
        while(true) {
            val curHead = head.value
            if (curHead.id * SEGM_SIZE > id) {
                return
            }
            val curHeadNext = curHead.next.value ?: dumby(id=curHead.id + 1)
            if (head.compareAndSet(curHead, curHeadNext)) {
                return
            }
        }
    }

    private fun moveTail(id: Int) {
        while(true) {
            val curTail = tail.value
            if (curTail.id * SEGM_SIZE > id) {
                return
            }
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
            val curTail = tail.value
            val s = getSegm(curTail, i / SEGM_SIZE)
            moveTail(i)
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
            val s = getSegm(curHead, i / SEGM_SIZE)
            moveHead(i)
            if (s.arr[i % SEGM_SIZE].compareAndSet(null, POISONED)) {
                continue
            }
            return s.arr[i % SEGM_SIZE].value as E
        }
    }
}

private val POISONED = Any()

