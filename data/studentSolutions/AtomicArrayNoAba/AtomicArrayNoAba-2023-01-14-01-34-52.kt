import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {

//        if (index1 == index2) {
//            if (expected1 == expected2) {
//                return cas(index1, expected1, update1 + update2)
//            }
//        }

        if (cas(index1, expected1, update2)) {
            if (cas(index2, expected2, update1)) {
                return true
            }
            else{
                cas(index1, expected1, update1)
                return false
            }
        }
        else {
            return false
        }
    }

    enum class Outcome {
        UNDECIDED,
        SUCCESS,
        FAIL
    }

    class CASNDescriptor <E>(a: Int, expectA: E, updateA: E, b: Int, expectB: E, updateB: E){
        val outcome = atomic(Outcome.UNDECIDED)
    }
}
