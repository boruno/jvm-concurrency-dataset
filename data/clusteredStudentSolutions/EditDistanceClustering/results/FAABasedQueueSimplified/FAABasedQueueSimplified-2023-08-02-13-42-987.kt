package day2

import day1.*
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

    fun retryDequeue(): Boolean {
        while (true) {
            val currentDeqIdx = deqIdx.value
            val currentEndIdx = enqIdx.value

            if (currentEndIdx != enqIdx.value) continue
            return currentDeqIdx >= currentEndIdx
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
//
//        while (true) {
//            if (retryDequeue()) continue
//
//            val i = deqIdx.getAndIncrement()
//
//            if (infiniteArray[i].compareAndSet(null, POISONED)) {
//                continue
//            }
//
//            return infiniteArray[i].value as E
//        }

        while (true) {
            val i = deqIdx.getAndIncrement()
            if (i in 0..1023) { // Check for array bounds
                val element = infiniteArray[i].value
                if (element !== POISONED) {
                    infiniteArray[i].compareAndSet(element, POISONED)
                    return element as E
                }
            } else {
                // Handle underflow, the queue is empty.
                return null
            }
        }


    }


}

// TODO: poison cells with this value.
private val POISONED = Any()

