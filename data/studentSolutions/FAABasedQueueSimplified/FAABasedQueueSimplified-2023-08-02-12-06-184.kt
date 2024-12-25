//package day2

import day1.*
import kotlinx.atomicfu.*
import kotlin.contracts.contract

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {

        while (true) {
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
//        val i = enqIdx.value
//        enqIdx.value = i + 1
            enqIdx.getAndIncrement()
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.

            if (infiniteArray[enqIdx.value].compareAndSet(null, element)) return
        }
    }

    fun retryDequeue(): Boolean{
        while (true) {
            val save_d = deqIdx.value
            val save_e = enqIdx.value

            if (save_e != enqIdx.value) continue
            return save_d >= save_e
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if(retryDequeue()) continue
//            if (enqIdx.value <= deqIdx.value) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
//        val i = deqIdx.value
//        deqIdx.value = i + 1


            deqIdx.getAndIncrement()

            if (infiniteArray[deqIdx.value].compareAndSet(null, POISONED)) {
                continue
            }


            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            return infiniteArray[deqIdx.value].value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
