import kotlinx.atomicfu.*
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.concurrent.atomic.AtomicReference

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)


    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        while (true) {
            val v = a[index].value!!
            if (v is AtomicArrayNoAba<*>.Desc) {
                (v as AtomicArrayNoAba<E>.Desc).complete()
                continue
            }
            return v as E
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        val v = get(index)
        if (v is AtomicArrayNoAba<*>.Desc) {
            (v as AtomicArrayNoAba<E>.Desc).complete()
        }
        return a[index].compareAndSet(expected, update)
    }

    private inner class Desc(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ) {
        private val okOrder = (index1 < index2)
        val indexA: Int = min(index1, index2)
        val indexB: Int = max(index1, index2)
        val expA: E = if (okOrder) expected1 else expected2
        val expB: E = if (okOrder) expected2 else expected1
        val updA: E = if (okOrder) update1 else update2
        val updB: E = if (okOrder) update2 else update1

        val completed = atomic(false)

        fun complete() {
            if (completed.compareAndSet(false, a[indexB].compareAndSet(expB, updB))) {
                println(this.toString())
            }
            if (completed.value) {
                a[indexA].value = updA
            } else {
                a[indexA].value = expA
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            if (expected1 == expected2 && update1 == update2) {
                return cas(index1, expected1, update1)
            }
            return false
        }
        val desc = Desc(index1, expected1, update1, index2, expected2, update2)
        val v1 = a[desc.indexA].value
        if (v1 is AtomicArrayNoAba<*>.Desc) {
            (v1 as AtomicArrayNoAba<E>.Desc).complete()
        }
        val v2 = a[desc.indexB].value
        if (v2 is AtomicArrayNoAba<*>.Desc) {
            (v2 as AtomicArrayNoAba<E>.Desc).complete()
        }

        if (a[desc.indexA].compareAndSet(desc.expA, desc)) {
            desc.complete()
            return desc.completed.value
        }
        return false
    }
}