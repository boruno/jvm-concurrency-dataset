import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Node<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Node(initialValue, false)
    }

    fun get(index: Int) =
        a[index].value!!.x

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(Node(expected, false), Node(update, false))

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (a[index1].compareAndSet(Node(expected1, false), Node(update1, true))) {

            if (a[index2].compareAndSet(Node(expected2, false), Node(update2, true))) {
                a[index1].compareAndSet(Node(update1, true), Node(update1, false))
                a[index2].compareAndSet(Node(update2, true), Node(update2, false))
                return true
            } else {
                a[index1].compareAndSet(Node(update1, true), Node(expected1, false))
                return false
            }

        }

        return false
    }
}

class Node<E>(val x: E, val blocked: Boolean)