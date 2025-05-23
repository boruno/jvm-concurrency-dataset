//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            val value = infiniteArray[i].value

            if (value != POISONED && infiniteArray[i].compareAndSet(value, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (shouldNotTryDeque()) {
                return null
            }

            val i = deqIdx.getAndIncrement()
            val value = infiniteArray[i].value

            if (value == null && infiniteArray[i].compareAndSet(null, POISONED)) {
                continue
            }

            return infiniteArray[i].value as E
        }
    }

    private fun shouldNotTryDeque(): Boolean {
        while (true) {
            val curDeq = deqIdx.value
            val curEnq = enqIdx.value

            @Suppress("KotlinConstantConditions")
            if (curDeq == deqIdx.value) {
                return curEnq >= curDeq
            }
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
