import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
class AtomicArrayNoAba<E>(size: Int, initialValue: E) {

    private val arr = Array(size) { Ref(initialValue) }

    fun get(index: Int): E = arr[index].value

    fun set(index: Int, value: E) = run {
        arr[index].value = value
    }

    fun cas(index: Int, expected: E, update: E) = arr[index].cas(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            return if (expected1 == expected2) arr[index1].cas(expected1, ((update2 as Int) + 1) as E)
            else false
        }
        return if (index1 < index2) {
            val desc = CAS2Descriptor(arr[index1], expected1, update1, arr[index2], expected2, update2)
            if (arr[index1].cas(expected1, desc)) desc.complete() else false
        } else {
            val desc = CAS2Descriptor(arr[index2], expected2, update2, arr[index1], expected1, update1)
            if (arr[index2].cas(expected2, desc)) desc.complete() else false
        }
    }
}

@Suppress("UNCHECKED_CAST")
class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    var value: T
        set(value) {
            v.loop { cur ->
                if (cur is Descriptor) cur.complete()
                else if (v.compareAndSet(cur, value)) return
            }
        }
        get() {
            v.loop { cur ->
                if (cur is Descriptor) cur.complete()
                else return cur as T
            }
        }


    fun cas(expect: Any?, update: Any?): Boolean {
        v.loop { cur ->
            if (cur is Descriptor) cur.complete()
            else if (v.compareAndSet(cur, update)) return true
        }
    }
}

private abstract class Descriptor {
    abstract fun complete(): Boolean
}


private class CAS2Descriptor<T>(
    val a: Ref<T>, val expectA: T, val updateA: T,
    val b: Ref<T>, val expectB: T, val updateB: T,
) : Descriptor() {
    val outcome: Ref<Boolean?> = Ref(null)

    override fun complete(): Boolean {
        if (b.v.value != this) b.cas(expectB, this)
        outcome.v.compareAndSet(null, b.v.value == this)

        val aValue = if (outcome.value!!) updateA else expectA
        val bValue = if (outcome.value!!) updateB else expectB

        a.v.compareAndSet(this, aValue)
        b.v.compareAndSet(this, bValue)

        return outcome.value!!
    }
}