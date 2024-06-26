import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) array[i].value = initialValue
    }

    fun get(index: Int) =
        array[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        array[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        while (true) {
            return if (array[index1].compareAndSet(expected1, update1)) {
                if (array[index2].compareAndSet(expected2, update2)) {
                    true
                } else {
                    array[index1].compareAndSet(update1, expected1)
                    false
                }
            } else {
                false
            }
        }


//        return atomic {
//            if (array[index1].value == expected1 && array[index2].value == expected2) {
//                array[index1].value = update1
//                array[index2].value = update2
//                true
//            } else {
//                false
//            }
//        }.value()
    }


}