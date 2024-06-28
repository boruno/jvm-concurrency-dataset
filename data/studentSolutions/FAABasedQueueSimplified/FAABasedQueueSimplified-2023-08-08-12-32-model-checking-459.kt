package day2

import day1.*
import java.util.concurrent.atomic.*
import kotlin.math.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            val idx = enqIdx.getAndAdd(1)
            if (infiniteArray.compareAndSet(idx.toInt(), null, element)) {
                break
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            val deq = deqIdx.get()
            val enq = enqIdx.get()
            if (deq >= enq) {
                return null
            }
//            if (!shouldDequeue()) {
//                return null
//            }
            val idx = deqIdx.getAndAdd(1)
            var v = infiniteArray.get(idx.toInt())
            if (v != null) {
                return v as E
            }
            v = infiniteArray.getAndSet(idx.toInt(), POISONED)
            if (v != null) {
                return v as E
            }
        }
    }

    fun shouldDequeue(): Boolean {
        while (true) {
            val enq0 = enqIdx.get()
            val deq = deqIdx.get()
            val enq1 = enqIdx.get()
            if (enq0 == enq1) {
                return deq < enq0;
            }
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
}

// TODO: poison cells with this value.
private val POISONED = Any()
