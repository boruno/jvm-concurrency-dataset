//package day2

import day1.*
import kotlinx.atomicfu.*

@Suppress("KotlinConstantConditions")
class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (shouldNotTryDeque()) return null
            val i = deqIdx.getAndIncrement()
            val element = infiniteArray[i].value
            if (element == null) {
                if (infiniteArray[i].compareAndSet(null, POISONED)) {
                    return infiniteArray[i].value as E
                }
            } else {
                return element as E
            }
        }
    }

    fun shouldNotTryDeque(): Boolean {
        while (true) {
            val curDeqInx = deqIdx.value
            val curEnqInx = enqIdx.value
            if (curDeqInx != deqIdx.value) continue
            return curDeqInx >= curEnqInx
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
