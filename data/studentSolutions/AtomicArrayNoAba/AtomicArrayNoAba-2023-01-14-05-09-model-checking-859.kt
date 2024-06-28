import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
//    private val a = atomicArrayOfNulls<Element<E>>(size)
    private val a = atomicArrayOfNulls<E?>(size)

    init {
//        for (i in 0 until size) {
//            a[i].value = Element(initialValue)
//        }
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        var result = a[index].value

        while (true) {
            if (result == null) {
                result = a[index].value
                continue
            }
            else {
                break
            }
        }


        return result!!
    }

    fun cas(index: Int, expected: E, update: E) =
//        a[index].value?.element?.compareAndSet(expected, update)
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {

        val thisCASNDescriptor = CASNDescriptor(index1, expected1, update1, index2, expected2, update2)

        if (a[index1].compareAndSet(expected1, null)) {
            if (a[index2].compareAndSet(expected2, null)) {
                a[index1].compareAndSet(null, update1)
                a[index2].compareAndSet(null, update2)
                return true
            }
            else {
                a[index1].compareAndSet(null, expected1)
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

    class CASNDescriptor<E>(index1: Int, expect1: E, update1: E, index2: Int, expect2: E, update2: E){
        val outcome = atomic(Outcome.UNDECIDED)
    }

    class Element<E>(e: E){
        val casnDescriptor = AtomicReference<CASNDescriptor<E>?>(null)
        val element = atomic(e)
    }
}
