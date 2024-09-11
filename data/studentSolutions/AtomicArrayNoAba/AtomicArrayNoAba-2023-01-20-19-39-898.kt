import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int) = a[index].get() as E

    fun cas(index: Int, expected: E, update: E) = a[index].cas(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            @Suppress("UNCHECKED_CAST")
            return cas(index1, expected1, (update1 as Int + update2 as Int - expected1 as Int) as E)
        }

        return when {
            (index1 <= index2) -> {
                val descriptor = DescriptorCAS2(a[index2], expected2, update2, a[index1], expected1, update1)
                a[index2].cas(expected2, descriptor) && descriptor.complete()
            }

            else -> {
                val descriptor = DescriptorCAS2(a[index1], expected1, update1, a[index2], expected2, update2)
                a[index1].cas(expected1, descriptor) && descriptor.complete()
            }
        }
    }
}

private abstract class Descriptor {
    abstract fun complete(): Boolean
}

private class DescriptorDCSS(
    val a1: Ref, val expected1: Any?, val update1: Any?,
    val a2: Ref, val expected2: Any?
) : Descriptor() {
    val status = Ref(null)
    override fun complete(): Boolean {
        status.v.compareAndSet(null, a2.get() == expected2)
        when {
            status.v.value as Boolean -> {
                a1.v.compareAndSet(this, update1)
            }

            else -> {
                a1.v.compareAndSet(this, expected1)
            }
        }
        return status.get() as Boolean
    }
}

private class DescriptorCAS2(
    val a1: Ref, val expected1: Any?, val update1: Any?,
    val a2: Ref, val expected2: Any?, val update2: Any?
) : Descriptor() {
    val status = Ref(null)
    override fun complete(): Boolean {
        val descriptor = DescriptorDCSS(a2, expected2, this, status, null)
        val res = a2.v.value == this || when {
            (!a2.cas(expected2, descriptor)) -> false
            else -> descriptor.complete()
        }

        status.v.compareAndSet(null, res)
        when {
            status.v.value as Boolean -> {
                a1.v.compareAndSet(this, update1)
                a2.v.compareAndSet(this, update2)
            }

            else -> {
                a1.v.compareAndSet(this, expected1)
                a2.v.compareAndSet(this, expected2)
            }
        }
        return status.get() as Boolean
    }
}

private class Ref(initial: Any?) {
    val v = atomic(initial)

    fun get(): Any? {
        while (true) {
            when (val value = v.value) {
                is Descriptor -> value.complete()
                else -> return value
            }
        }
    }

    fun cas(expected: Any?, update: Any?): Boolean {
        while (true) {
            if (v.compareAndSet(expected, update)) return true
            val value = v.value
            if (value !is Descriptor && value != expected) return false
            (value as Descriptor).complete()
        }
    }
}