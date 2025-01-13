//package day2

import java.util.concurrent.atomic.*
import kotlin.math.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            val idx = enqIdx.getAndIncrement().toInt()
            if (infiniteArray.compareAndSet(idx, null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (compareIdx2()) return null // queue is empty
            val idx = deqIdx.getAndIncrement().toInt()
            if (infiniteArray.compareAndSet(idx, null, POISONED)) continue
            return infiniteArray.get(idx) as E
        }
    }

    override fun validate() {
        for (i in 0 until min(deqIdx.get().toInt(), enqIdx.get().toInt())) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `deqIdx = ${deqIdx.get()}` at the end of the execution"
            }
        }
        for (i in max(deqIdx.get().toInt(), enqIdx.get().toInt()) until infiniteArray.length()) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `enqIdx = ${enqIdx.get()}` at the end of the execution"
            }
        }
    }

    private fun compareIdx2(): Boolean {
        while (true) {
            val curEnq = enqIdx.get()
            val curDeq = deqIdx.get()
            val newCurEnq = enqIdx.get()
            if (newCurEnq != curEnq) continue
            return curEnq <= curDeq
        }
    }

    private fun compareIdx(): Boolean {
        while (true) {
            val curDeq = deqIdx.get()
            val curEnq = enqIdx.get()
            val newCurDeq = deqIdx.get()
            if (newCurDeq != curDeq) continue
            return curDeq < curEnq
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
