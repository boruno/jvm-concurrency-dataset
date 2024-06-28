import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0..size-1) a[i].value = initialValue
    }

    fun get(index: Int): E {
        a[index].loop { value ->
            return value as E
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            val value = get(index)

            if (value != expected) return false
            if (a[index].compareAndSet(expected, update)) return true
        }
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        while (true) {
            return if (a[index1].value == expected1 && a[index2].value == expected2) {
                a[index1].compareAndSet(expected1, update1)
                a[index2].compareAndSet(expected2, update2)
                true
            } else {
                false
            }
        }
    }
}