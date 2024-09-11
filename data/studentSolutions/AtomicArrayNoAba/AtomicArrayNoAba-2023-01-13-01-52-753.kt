import kotlinx.atomicfu.atomicArrayOfNulls

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)
    // null - not in progress
    // false - progress
    // true - end
    private val lockArray = atomicArrayOfNulls<Boolean>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }


    private enum class CurrStatusMCAS{
        OK, ERROR, IN_PROGRESS
    }
    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {

        synchronized(get(index1)) {
            synchronized(get(index2)) {

                if (a[index1].value != expected1 || a[index2].value != expected2) return false
                a[index1].value = update1
                a[index2].value = update2
                return true
            }
        }
    }

//    fun cas2(
//        index1: Int, expected1: E, update1: E,
//        index2: Int, expected2: E, update2: E
//    ): Boolean {
//        // TODO this implementation is not linearizable,
//        // TODO a multi-word CAS algorithm should be used here.
//        var currentSuccess = true
//
//        var finished1 = false
//        var finished2 = false
//        lockArray[index1].value = false
//        lockArray[index2].value = false
//        while (true) {
//            if (finished1 && finished2) break
//            val currFirst = get(index1)
//            val currSecond = get(index2)
//            if (currFirst != update1) {
//                if (currFirst != expected1) {
//                    currentSuccess = false
//                    break
//                }
//                if (lockArray[index1].value != false) break
//
//                if (cas(index1, currFirst, update1)) finished1 = true
//            }
//            if (currSecond != update2) {
//                if (currSecond != expected2) {
//                    currentSuccess = false
//                    break
//                }
//                if (lockArray[index2].value != false) break
//
//                if (cas(index2, currSecond, update2)) finished2 = true
//            }
//        }
//
//        return currentSuccess
//
//    }

}