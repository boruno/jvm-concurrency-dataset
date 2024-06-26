import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)
    private val b = atomicArrayOfNulls<Boolean>(size)

    init {
        for (i in 0 until size) {
            a[i].value = initialValue
            b[i].value = false
        }
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E): Boolean {
        if (b[index].compareAndSet(false, true)) {
            val res = a[index].compareAndSet(expected, update)
            b[index].getAndSet(false)
            return res
        }
        return false
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        return if (b[index1].compareAndSet(false, true)) {
            if (a[index1].compareAndSet(expected1, update1)) {
                if (b[index2].compareAndSet(false, true)) {
                    if (a[index2].compareAndSet(expected2, update2)) {
                        b[index1].getAndSet(false)
                        b[index2].getAndSet(false)
                        true
                    } else {
                        a[index1].compareAndSet(update1, expected1)
                        b[index1].getAndSet(false)
                        b[index2].getAndSet(false)
                        false
                    }
                } else {
                    a[index1].compareAndSet(update1, expected1)
                    b[index1].getAndSet(false)
                    false
                }
            } else {
                b[index1].getAndSet(false)
                false
            }
        } else {
            false
        }
    }
}