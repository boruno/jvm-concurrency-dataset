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
             index2: Int, expected2: E, update2: E) = Ref(
        //TODO this implementation is not linearizable,
        //TODO a multi-word CAS algorithm should be used here.
        if (a[index1].value == expected1 && a[index2].value == expected2) {
            a[index1].value = update1
            a[index2].value = update2
            true
        } else false
    ).value
}

class Ref<T>(initial: T) {
    private val v = atomic<Any?>(initial)

    var value: T
        get() = v.value as T
        set(value: T) {
            v.value = value
        }
}