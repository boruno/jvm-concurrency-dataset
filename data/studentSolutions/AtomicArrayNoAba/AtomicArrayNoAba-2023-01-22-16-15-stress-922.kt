import kotlinx.atomicfu.*
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicReference

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int): E = a[index].value!!.value

    fun cas(index: Int, expected: E, update: E): Boolean {
        val cur = a[index].value!!
        if (cur.value != expected) return false
        return cur.v.compareAndSet(expected, update)
    }



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

        val descriptor = CASNDescriptor(
            aValue, expectedA, updateA,
            bValue, expectedB, updateB)

        if (aValue.value != expectedA) return false
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
        val v = atomic<Any?>(initial)

        var value: T
            get() {
                v.loop {cur ->
                    when(cur) {
                        is Descriptor -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when(cur) {
                        is Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd)) return
                    }
                }
            }
    }

    private abstract class Descriptor {
        abstract fun complete() : Boolean
    }

    private class CASNDescriptor<A, B>(
        val a: Ref<A>, val expectedA: A, val updatedA: A,
        val b: Ref<B>, val expectedB: B, val updatedB: B
    ) : Descriptor() {
        private val state : Ref<Int> = Ref(-1)
        override fun complete(): Boolean {
            while (true) {
                when (state.value) {
                    -1 -> {
                        /*
                        if (b.v.value != expectedB) {
                            state.compareAndSet(-1, 0)
                            continue
                        }
                         */
                        val descriptor = RDCSSDescriptor(
                            b, expectedB, updatedB,
                            state, -1
                        )
                        if (b.v.compareAndSet(expectedB, descriptor)) descriptor.complete()
                        if (b.v.value as? CASNDescriptor<*, *> == this) {
                            state.v.compareAndSet(-1, 1)
                            continue
                        }
                        state.v.compareAndSet(-1, 0)
                    }
                    0 -> {
                        a.v.compareAndSet(this, expectedA)
                        return false
                    }
                    1 -> {
                        a.v.compareAndSet(this, updatedA)
                        b.v.compareAndSet(this, updatedB)
                        return true
                    }
                }
            }
        }
    }

    private class RDCSSDescriptor<A, B>(
        val a: Ref<A>, val expectA: A, val updateA: A,
        val b: Ref<B>, val expectB: B
    ) : Descriptor() {
        override fun complete(): Boolean {
            val update = if (b.value === expectB) updateA else expectA
            a.v.compareAndSet(this, update)
            return true
        }

    }
}

