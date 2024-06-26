import kotlinx.atomicfu.*
import java.sql.Ref

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        while(true) {
            val value = a[index].value
            if (value is AtomicArrayNoAba<*>.CASNDescriptor<*>) {
                value.complete()
                continue
            }
            return value as E
        }
    }

    fun cas(index: Int, expected: Any, update: Any): Boolean {
        while(true) {
            val value = a[index].value
            if (value is AtomicArrayNoAba<*>.CASNDescriptor<*>) {
                value.complete()
                continue
            }
            if (value != expected)
                return false
            if (!a[index].compareAndSet(value, update))
                continue
            return true
        }
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        val indexA: Int
        val indexB: Int
        val expectedA: E
        val expectedB: E
        val updateA: E
        val updateB: E
        if (index1 < index2)
        {
            indexA = index1
            indexB = index2
            expectedA = expected1
            expectedB = expected2
            updateA = update1
            updateB = update2
        }
        else
        {
            indexA = index2
            indexB = index1
            expectedA = expected2
            expectedB = expected1
            updateA = update2
            updateB = update1
        }
        if (get(indexA) != expectedA)
            return false
        if (get(indexB) != expectedB)
            return false
        val descriptor = CASNDescriptor(indexA, expectedA, updateA, indexB, expectedB, updateB)
        while(true)
        {
            val aValue = a[indexA].value
            if (aValue is AtomicArrayNoAba<*>.CASNDescriptor<*>)
            {
                aValue.complete()
                continue
            }
            if (aValue != expectedA)
                return false
            if (!a[indexA].compareAndSet(aValue, descriptor))
                continue
            break
        }
        while(descriptor.outcome.value == result.UNDECIDED)
        {
            val bValue = a[indexB].value
            if (bValue is AtomicArrayNoAba<*>.CASNDescriptor<*>)
            {
                bValue.complete()
                continue
            }
            if (bValue != expectedB)
            {
                if(descriptor.outcome.compareAndSet(result.UNDECIDED, result.FAIL)) {
                    a[indexA].compareAndSet(descriptor, expectedA)
                    return false
                }
                continue
            }
            if (!a[indexB].compareAndSet(bValue, descriptor))
                continue
            break
        }
        if (descriptor.outcome.value == result.FAIL)
        {
            descriptor.complete()
            return false
        }
        if (descriptor.outcome.value == result.SUCC)
        {
            descriptor.complete()
            return true
        }
        descriptor.outcome.compareAndSet(result.UNDECIDED, result.SUCC)
        if (descriptor.outcome.value == result.SUCC)
        {
            a[indexA].compareAndSet(descriptor, updateA)
            a[indexB].compareAndSet(descriptor, updateB)
            return true
        }
        if (descriptor.outcome.value == result.FAIL)
        {
            descriptor.complete()
        }
        return false
    }
    inner class CASNDescriptor<E>(val indexA: Int, val expectA: E, val updateA: E,
                                  val indexB: Int, val expectB: E, val updateB: E) {
        val outcome = atomic(result.UNDECIDED)
        fun complete() {
            if (outcome.value == result.UNDECIDED)
            {
                while(outcome.value == result.UNDECIDED)
                {
                    val bValue = a[indexB].value
                    if (bValue === this)
                    {
                        outcome.compareAndSet(result.UNDECIDED, result.SUCC)
                        break
                    }
                    if (bValue is AtomicArrayNoAba<*>.CASNDescriptor<*>)
                    {
                        bValue.complete()
                        continue
                    }
                    if (bValue != expectB)
                    {
                        outcome.compareAndSet(result.UNDECIDED, result.FAIL)
                        a[indexA].compareAndSet(this, expectA)
                        return
                    }
                    if (!a[indexB].compareAndSet(bValue, this))
                        continue
                    outcome.compareAndSet(result.UNDECIDED, result.SUCC)
                    break
                }
            }
            if (outcome.value == result.FAIL)
            {
                if (a[indexA].value === this)
                {
                    a[indexA].compareAndSet(this, expectA)
                }
                return
            }
            if (outcome.value == result.SUCC)
            {
                a[indexA].compareAndSet(this, updateA)
                a[indexB].compareAndSet(this, updateB)
                return
            }
        }
    }
}


enum class result {UNDECIDED, SUCC, FAIL}