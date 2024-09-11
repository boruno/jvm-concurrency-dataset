import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    class Ref<T>(initial: T) {
        private val v = atomic(initial)

        var value: T
            get() = v.value
            set(value) {
                v.value = value
            }

        fun cas(expected: T, update: T) = v.compareAndSet(expected, update)
        fun gas(update: T) = v.getAndSet(update)

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
    ): Boolean {
        val indexes = listOf(index1, index2)
            .zip(listOf(expected1, expected2))
            .zip(listOf(update1, update2)) { (index, expected), b -> CasData(index, expected, b) }
            .sortedBy { it.index }

        return if (indexes.all { a[it.index].value == it.expected }) {
            indexes.forEach {
                a[it.index].value?.gas(it.update)
            }
            true
        } else {
            false
        }
    }
}

data class CasData<E>(
    val index: Int,
    val expected: E,
    val update: E
)