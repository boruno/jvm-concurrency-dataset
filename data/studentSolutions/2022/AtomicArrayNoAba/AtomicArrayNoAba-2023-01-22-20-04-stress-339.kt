
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
        if (status == "a") {
            status = "success"
            val val1 = a[index1].value
            val val2 = a[index2].value
            if (val1 != expected1 || val2 != expected2) {
                status = "fail"
            }
            a[index1].compareAndSet(expected1, update1)
            a[index2].compareAndSet(expected2, update2)
        }
        return status == "success"
    }

    var status = "a"
    
}
