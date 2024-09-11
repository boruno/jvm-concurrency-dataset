package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {

        while(true) {
            val i = enqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, element)){
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while(true) {
            if (deqIdx.value <= enqIdx.value){ return null}
            val i = deqIdx.getAndIncrement()
            val el = infiniteArray[i]
            if (el.compareAndSet(null, POISONED)){
                return el.value as E?
            }
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
