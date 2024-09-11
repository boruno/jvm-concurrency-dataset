import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = Array(size) {
        Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E): Boolean {
        val curValue = a[index].value
        return if (curValue == expected) {
            a[index].casV(expected as Any, update as Any)
        } else false
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {

        return if (index1 < index2) {
            cas2Outer(a[index1], expected1, update1, a[index2], expected2, update2)
        } else {
            cas2Outer(a[index2], expected2, update2, a[index1], expected1, update1)
        }
    }
}