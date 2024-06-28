import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        while (true) {
            @Suppress("UNCHECKED_CAST")
            when (val value = a[index].value) {
                is AtomicArrayNoAba<*>.Descriptor<*> -> value.complete()
                else -> return value as E
            }
        }
    }

    fun cas(index: Int, expected: Any, update: Any): Boolean {
        while (true) {
            when (val value = a[index].value) {
                is AtomicArrayNoAba<*>.Descriptor<*> -> value.complete()
                else -> return a[index].compareAndSet(expected, update)
            }
        }
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        val value = a[index1].value
        if (value is AtomicArrayNoAba<*>.Descriptor<*>) {
            value.complete()
        }

        val descriptor = Descriptor(index1, expected1, update1, index2, expected2, update2)
        if (a[index1].compareAndSet(expected1, descriptor)) {
            descriptor.complete()
            return descriptor.status.value == SUCCESS
        } else {

        }
        return false
    }

    inner class Descriptor<E : Any>(
        private val indexA: Int, private val expectedA: E, private val updateA: E,
        private val indexB: Int, private val expectedB: E, private val updateB: E
    ) {

        val status: AtomicRef<Any> = atomic(UNDECIDED)

        fun complete() {
            if (a[indexB].compareAndSet(expectedB, this)) {
                status.value = SUCCESS
                a[indexB].value = updateB
                a[indexA].value = updateA
            } else if (status.compareAndSet(UNDECIDED, FAILED)) {
                a[indexA].value = expectedA
            }
        }

    }
}

val UNDECIDED = Any()
val SUCCESS = Any()
val FAILED = Any()