import kotlinx.atomicfu.atomicArrayOfNulls

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)
    private val lockArray = atomicArrayOfNulls<Boolean>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (a[index1].value != expected1 || a[index2].value != expected2) return false
        if (index1 == index2 && update1 != update2) return false

        while (true) {
            if (lockArray.get(index1).compareAndSet(null, true)) {
                while (true) {
                    //if (lockArray.get(index2).compareAndSet(null, true)) {
                        a[index1].value = update1
                        a[index2].value = update2

                        //lockArray[index2].value = null
                        lockArray[index1].value = null
                        return true
                   //}
                }
            }
            if (lockArray.get(index2).compareAndSet(null, true)) {
                while (true) {
                    //if (lockArray.get(index1).compareAndSet(null, true)) {
                        a[index1].value = update1
                        a[index2].value = update2

                       // lockArray[index1].value = null
                        lockArray[index2].value = null
                        return true
                   // }
                }
            }
        }
    }

}