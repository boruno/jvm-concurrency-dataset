import kotlinx.atomicfu.*
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicReference

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int): E {
        while (true) {
            val cur = a[index].value!!.v.get()
            if (cur is Descriptor<*, *>) {
                cur.complete()
                continue
            }
            return cur as E
        }
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.v.compareAndSet(expected, update)


    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (index1 == index2) {
            if (expected1 != expected2) return false
            if (update1 != update2) return false
            if (update1 is Int) {
                if (cas(index1, expected1, ((update1 as Int) + (update2 as Int) - (expected1 as Int)) as E))
                    return true
                return false
            }
            if (cas(index1, expected1, update1)) return true
            return false
        }

        // val aValue: Ref<E> = a[index1].value!!
        // val bValue: Ref<E> = a[index2].value!!

        val aValue: Ref<E>
        val bValue: Ref<E>
        val expectedA: E
        val expectedB: E
        val updateA: E
        val updateB: E
        if (index1 < index2) {
            aValue = a[index1].value!!
            expectedA = expected1
            updateA = update1
            bValue = a[index2].value!!
            expectedB = expected2
            updateB = update2
        } else {
            aValue = a[index2].value!!
            expectedA = expected2
            updateA = update2
            bValue = a[index1].value!!
            expectedB = expected1
            updateB = update1
        }

        val descriptor = Descriptor(
            aValue, expectedA, updateA,
            bValue, expectedB, updateB)

        if (!aValue.v.compareAndSet(expectedA, descriptor)) return false
        return descriptor.complete()

        /*
        if (a[index1].value != expected1 || a[index2].value != expected2) return false
        a[index1].value = update1
        a[index2].value = update2
        return true

         */
    }

    private class Ref<T>(initial: T) {
        val v: AtomicReference<Any?> = AtomicReference(initial)
    }

    private class Descriptor<A, B>(
        val a: Ref<A>, val expectedA: A, val updatedA: A,
        val b: Ref<B>, val expectedB: B, val updatedB: B
    ) {
        private val state :AtomicRef<String> = atomic("UNDECIDED")
        fun complete(): Boolean {
            b.v.compareAndSet(expectedB, this)
            if (b.v.get() === this) {
                state.compareAndSet("UNDECIDED", "SUCCESS")
            } else {
                state.compareAndSet("UNDECIDED", "FAIL")
            }

            return when(state.value) {
                "FAIL" -> {
                    a.v.compareAndSet(this, expectedA)
                    false
                }

                "SUCCESS" -> {
                    a.v.compareAndSet(this, updatedA)
                    b.v.compareAndSet(this, updatedB)
                    true
                }

                else -> false
            }
        }
    }
}

