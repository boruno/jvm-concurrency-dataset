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
        val tmp = start
        val tmpNext = tmp.next.value
        val newSeg = Segment<E>(id)

        if (tmp.id == id)
            return tmp
        if (tmpNext != null)
            return tmpNext
        else {
            if (tmp.next.compareAndSet(null, newSeg))
                return newSeg
            return tmp.next.value as Segment<E>
        } // ???? what about next ????
    }

    fun moveTailForward(newTail: Segment<E>) {
        while (true)
        {
            val currentTail = tail.value ?: continue
            if (newTail.id > currentTail.id)
            {
                if (tail.compareAndSet(currentTail, newTail))
                    return
                else
                    continue
            }
            else {
                return
            }

        }
    }

    fun moveHeadForward(newHead: Segment<E>) {
        while (true) {
            val currentHead = head.value ?: continue

            if (newHead.id  > deqIdx.value / SEGM_SIZE) // мы хотим сделать head таким, что до deqIdx не достать
                return
            if (newHead.id > currentHead.id)
            {
                if (head.compareAndSet(currentHead, newHead))
                    return
                else
                    continue
            }
            else {
                return
            }
        }
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val index = enqIdx.getAndIncrement()
            val s = findSegment(curTail, index / SEGM_SIZE)
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
//            deqIndex = deqIdx.value
            if (deqIndex >= enqIndex) return null
            val curHead = head.value
            val index = deqIdx.getAndIncrement()
            val s = findSegment(curHead, index / SEGM_SIZE)
            moveHeadForward(s) // another function
            if (s.infiniteArray[index % SEGM_SIZE].compareAndSet(null, POISONED))
                continue
            return s.infiniteArray[index].value as E?
        }
        //TODO("Implement me!")
    }

    class Segment<E>(val id: Int) {
        val infiniteArray = atomicArrayOfNulls<Any?>(SEGM_SIZE)
        val next : AtomicRef<Segment<E>?> = atomic<Segment<E>?>(null)

    }
}

private val POISONED = Any()
private val SEGM_SIZE = 4