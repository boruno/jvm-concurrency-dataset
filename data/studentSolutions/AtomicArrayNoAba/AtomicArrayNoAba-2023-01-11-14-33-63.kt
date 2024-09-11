import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        while (true) {
            val value = a[index].value

            if (value != null) {
                return value
            }
        }
    }
    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {

        while (true) {
            val value1 = a[index1]
            val value2 = a[index2]

            if ((value1.value != expected1) || (value2.value != expected2)) {
                return false
            } else {
                value1.getAndSet(null)

                if (value1.compareAndSet(expected1, update1)) {
                    value2.getAndSet(null)

                    if (value2.compareAndSet(expected2, update2)) {
                        return true
                    }
                }
                return false
            }
        }


        // this implementation is not linearizable,
        // a multi-word CAS algorithm should be used here.

//        if (a[index1].value != expected1 || a[index2].value != expected2) return false
//        a[index1].value = update1
//        a[index2].value = update2
//        return true
    }
}