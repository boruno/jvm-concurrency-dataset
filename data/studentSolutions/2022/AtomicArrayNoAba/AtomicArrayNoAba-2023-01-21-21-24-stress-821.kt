import kotlinx.atomicfu.*
import kotlin.math.exp

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) { a[i].value = initialValue }
    }

    fun get(index: Int): E {
        while (true) {
            val x = a[index].value
            if (x !is Descriptor<*>) { return x as E }
            helpDescriptor(x)
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            val x = get(index)
            if (x != expected) { return false }
            if (a[index].compareAndSet(x, update)) { return true }
        }
    }

    private fun helpDescriptor(descriptor: Descriptor<*>) {
        while (true) {
            when (descriptor.outcome.value) {
                Outcome.FAILED -> {
                    a[descriptor.index1].compareAndSet(descriptor, descriptor.expected1)
                    a[descriptor.index2].compareAndSet(descriptor, descriptor.expected2)
                    return
                }
                Outcome.SUCCESS -> {
                    a[descriptor.index1].compareAndSet(descriptor, descriptor.update1)
                    a[descriptor.index2].compareAndSet(descriptor, descriptor.update2)
                    return
                }
                else -> {}
            }

            val ind2 = a[descriptor.index2].value
            if (ind2 is Descriptor<*>) {
                if (ind2 != descriptor) {
                    helpDescriptor(ind2)
                } else {
                    descriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                }
                continue
            }

            if (ind2 != descriptor.expected2) {
                descriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAILED)
                continue
            }
            if (a[descriptor.index2].compareAndSet(ind2, descriptor)) {
                descriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                continue
            }
        }
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 > index2) {
            return cas2(index2, expected2, update2, index1, expected1, update1)
        }

        val descriptor = Descriptor<E>(index1, expected1, update1, index2, expected2, update2)
        while (true) {
            val ind1 = get(index1)
            if (ind1 != expected1) { return false }
            if (!a[index1].compareAndSet(ind1, descriptor)) {
                val x = a[index1].value
                if (x is Descriptor<*>) { continue }
                return false
            }

            helpDescriptor(descriptor)
            val outcome = descriptor.outcome.value
            assert(outcome != Outcome.UNDECIDED)
            return outcome == Outcome.SUCCESS
        }
    }
}

class Descriptor<E>(
    val index1: Int,
    val expected1: E,
    val update1: E,
    val index2: Int,
    val expected2: E,
    val update2: E
) {
    val outcome = atomic(Outcome.UNDECIDED)
}

enum class Outcome {
    UNDECIDED,
    SUCCESS,
    FAILED
}