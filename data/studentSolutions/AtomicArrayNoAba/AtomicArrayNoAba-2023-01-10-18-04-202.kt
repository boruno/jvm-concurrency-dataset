import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    class Ref<T>(initial: T) {
        private val v = atomic(initial)

        var value: T
            get() = v.value
            set(value: T) {
                v.value = value
            }

        fun cas(expected: T, update: T) = v.compareAndSet(expected, update)

    }

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.cas(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ) =
        if (a[index1].value?.value == expected1 && a[index2].value?.value == expected2) {
            a[index1].value?.value = update1
            a[index2].value?.value = update2
            true
        } else false
}