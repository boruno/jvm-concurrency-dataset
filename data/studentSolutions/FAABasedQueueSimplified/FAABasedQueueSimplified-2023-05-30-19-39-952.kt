//package day2

import kotlinx.atomicfu.*
import kotlin.contracts.contract

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, element)) return
        }
    }


    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            while (true) {
                val firstEnq = enqIdx.value
                val firstDeq = deqIdx.value
                if (firstDeq  > firstEnq) {
                    if (enqIdx.value <= deqIdx.value) {
                        return null
                    } else {
                        break
                    }
                }
            }
            val i = deqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, POISONED)) continue
            return infiniteArray[i].value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
