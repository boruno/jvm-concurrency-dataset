//package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicRef<Segment<E>>
    private val tail: AtomicRef<Segment<E>>

    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        val dummy = Segment<E>(0)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    fun findSegment(start: Segment<E>, id: Int): Segment<E> {
        var tmp = start
        val newSegm = Segment<E>(id)
        while (!tmp.next.compareAndSet(null, newSegm)) {
           if (tmp.id == id)
               return tmp
            tmp = tmp.next.value as Segment<E>
        }

        return tmp.next.value !!
    }

    fun moveTailForward(newTail: Segment<E>) {
        while (true) {
            if (tail.compareAndSet(tail.value, newTail))
                return
        }

    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail
            val index = enqIdx.getAndIncrement()
            val s = findSegment(curTail.value, index / SEGM_SIZE)
            moveTailForward(s)
            if (s.infiniteArray[index % SEGM_SIZE].compareAndSet(null, element))
                return
        }
        //TODO("Implement me!")
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            var deqIndex = deqIdx.value
            var enqIndex = enqIdx.value
            deqIndex = deqIdx.value
            if (deqIndex >= enqIndex) return null
            val curHead = head
            val index = deqIdx.getAndIncrement()
            val s = findSegment(curHead.value, index / SEGM_SIZE)
            moveTailForward(s)
            if (s.infiniteArray[index % SEGM_SIZE].compareAndSet(null, POISONED))
                continue
            return s.infiniteArray[index].value as E?
        }
        //TODO("Implement me!")
    }

    class Segment<E>(val id: Int?) {
        val infiniteArray = atomicArrayOfNulls<Any?>(SEGM_SIZE)
        val next : AtomicRef<Segment<E>?> = atomic<Segment<E>?>(null)

    }
}

private val POISONED = Any()
private val SEGM_SIZE = 8