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
        if (array[index1].value == expected1 && array[index2].value == expected2) {
            array[index1].value = update1
            array[index2].value = update2
            return true
        } else {
            return false
        }
    }


}