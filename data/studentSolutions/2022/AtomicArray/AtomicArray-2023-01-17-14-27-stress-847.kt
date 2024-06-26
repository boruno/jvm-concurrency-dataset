import kotlinx.atomicfu.*
import java.lang.ref.Reference
import java.util.concurrent.atomic.AtomicReference

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        a[index].loop { cur ->
            when (cur) {
                is AtomicArray<*>.RDCSSDescriptor<*> -> cur.complete()
                is AtomicArray<*>.CASNDescriptor<*> -> cur.complete()
                else -> return cur as E
            }
        }
    }


    fun set(index: Int, value: E) {
        a[index].loop { cur ->
            when (cur) {
                is AtomicArray<*>.RDCSSDescriptor<*> -> cur.complete()
                is AtomicArray<*>.CASNDescriptor<*> -> cur.complete()
                else -> if (a[index].compareAndSet(cur, value))
                    return
            }
        }
    }

    fun cas(index: Int, expected: Any, update: Any): Boolean {
        a[index].loop { cur ->
            when (cur) {
                is AtomicArray<*>.RDCSSDescriptor<*> -> cur.complete()
                is AtomicArray<*>.CASNDescriptor<*> -> cur.complete()
                else -> if (cur != expected) return false
                        else if (a[index].compareAndSet(expected, update)) return true
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        val indexA: Int
        val indexB: Int
        val expectedA: E
        val expectedB: E
        val updateA: E
        val updateB: E
        if (index1 == index2 && expected1 != expected2)
            return false

        if (index1 < index2) {
            indexA = index1
            indexB = index2
            expectedA = expected1
            expectedB = expected2
            updateA = update1
            updateB = update2
        } else {
            indexA = index2
            indexB = index1
            expectedA = expected2
            expectedB = expected1
            updateA = update2
            updateB = update1
        }
        val descriptor = CASNDescriptor(indexA, expectedA, updateA, indexB, expectedB, updateB)
        while (true) {
            val aValue = a[indexA].value
            if (aValue is AtomicArray<*>.RDCSSDescriptor<*>)
            {
                aValue.complete()
                continue
            }
            if (aValue is AtomicArray<*>.CASNDescriptor<*>) {
                aValue.complete()
                continue
            }
            if (aValue != expectedA)
                return false
            if (!a[indexA].compareAndSet(aValue, descriptor))
                continue
            break
        }
        val descriptorDCSS = RDCSSDescriptor(descriptor.outcome, result.UNDECIDED, indexB, expectedB, descriptor)
        if (!DCSS(descriptorDCSS)) {
            descriptor.outcome.compareAndSet(result.UNDECIDED, result.FAIL)
        } else {
            descriptor.outcome.compareAndSet(result.UNDECIDED, result.SUCC)
        }

        if (descriptor.outcome.get() == result.FAIL) {
            descriptor.complete()
            return false
        }
        if (descriptor.outcome.get() == result.SUCC) {
            descriptor.complete()
            return true
        }
        descriptor.outcome.compareAndSet(result.UNDECIDED, result.SUCC)
        if (descriptor.outcome.get() == result.SUCC) {
            a[indexA].compareAndSet(descriptor, updateA)
            a[indexB].compareAndSet(descriptor, updateB)
            return true
        }
        if (descriptor.outcome.get() == result.FAIL) {
            descriptor.complete()
        }
        return false
    }

    inner class CASNDescriptor<E>(
        val indexA: Int, val expectA: E, val updateA: E,
        val indexB: Int, val expectB: E, val updateB: E
    ) {
        val outcome: AtomicReference<result> = AtomicReference<result>(result.UNDECIDED)
        fun complete() {
            if (outcome.get() == result.UNDECIDED) {
                while (outcome.get() == result.UNDECIDED) {
                    val bValue = a[indexB].value
                    if (bValue === this) {
                        outcome.compareAndSet(result.UNDECIDED, result.SUCC)
                        break
                    }
                    if (bValue is AtomicArray<*>.CASNDescriptor<*>) {
                        bValue.complete()
                        continue
                    }
                    if (bValue is AtomicArray<*>.RDCSSDescriptor<*>) {
                        bValue.complete()
                        continue
                    }
                    if (bValue != expectB) {
                        if (outcome.compareAndSet(result.UNDECIDED, result.FAIL)) {
                            a[indexA].compareAndSet(this, expectA)
                            return
                        }
                        continue
                    }
                    if (!a[indexB].compareAndSet(bValue, this))
                        continue
                    outcome.compareAndSet(result.UNDECIDED, result.SUCC)
                    break
                }
            }
            if (outcome.get() == result.FAIL) {
                if (a[indexA].value === this) {
                    a[indexA].compareAndSet(this, expectA)
                }
                return
            }
            if (outcome.get() == result.SUCC) {
                a[indexA].compareAndSet(this, updateA)
                a[indexB].compareAndSet(this, updateB)
                return
            }
        }
    }

    inner class RDCSSDescriptor<E>(
        val valueControl: AtomicReference<result>, val expectedControl: result,
        val dataIndex: Int, val expectedData: E, val updatedData: Any
    ) {
        val outcome = atomic(result.UNDECIDED)
        fun complete() {
            val update: Any
            if(valueControl.get() == expectedControl) {
                outcome.compareAndSet(result.UNDECIDED, result.SUCC)
                a[dataIndex].compareAndSet(this, updatedData)
            }
            else {
                outcome.compareAndSet(result.UNDECIDED, result.FAIL)
                a[dataIndex].compareAndSet(this, expectedData)
            }
        }
    }


    fun DCSS(d: AtomicArray<E>.RDCSSDescriptor<E>): Boolean {
        while (true) {
            val data = a[d.dataIndex].value
            if (data is AtomicArray<*>.RDCSSDescriptor<*>) {
                data.complete()
                continue
            }
            if (data is AtomicArray<*>.CASNDescriptor<*>) {
                data.complete()
                continue
            }
            if (data != d.expectedData) {
                d.outcome.compareAndSet(result.UNDECIDED, result.FAIL)
                break
            }
            if (!a[d.dataIndex].compareAndSet(data, d))
                continue
            break
        }
        if (d.outcome.value == result.UNDECIDED) {
            d.complete()
        }
        if (d.outcome.value == result.SUCC) {
            a[d.dataIndex].compareAndSet(d, d.updatedData)
            return true
        }
        if (d.outcome.value == result.FAIL)
        {
            a[d.dataIndex].compareAndSet(d, d.expectedData)
            return false
        }
        throw Exception("We are not supposed to be here")
    }

}


enum class result { UNDECIDED, SUCC, FAIL }