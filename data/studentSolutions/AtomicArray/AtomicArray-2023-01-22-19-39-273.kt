import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) = a[index].value!!.value

    fun set(index: Int, value: E) {
        a[index].value!!.value = value
    }

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

        return descriptor.start()
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

    private class CASNDescriptor<A>(
        val a: Ref<A>, val expectedA: A, val updatedA: A,
        val b: Ref<A>, val expectedB: A, val updatedB: A
    ) : Descriptor() {
        private val state : Ref<Int> = Ref(-1)
        fun start(): Boolean {
            if (a.value != expectedA) {
                state.v.compareAndSet(-1, 0)
                if (state.value == 1) return complete()
                return false
            }
            if (a.v.compareAndSet(expectedA, this)) {
                return complete()
            }
            state.v.compareAndSet(-1, 0)
            if (state.value == 1) return complete()
            return false

        }
        override fun complete(): Boolean {
            if (b.v.value != this) {
                val descriptor = RDCSSDescriptor(
                    b, expectedB, this,
                    state, -1
                ).start()
                if (b.v.value != this) {
                    state.v.compareAndSet(-1, 0)
                    a.v.compareAndSet(this, expectedA)
                    if (state.value == 0) return false
                }
            }

            /*
            if (b.v.value != this) {
                if (state.value == 0) {
                    a.v.compareAndSet(this, expectedA)
                    return false
                }
                val descriptor = RDCSSDescriptor(
                    b, expectedB, this,
                    state, -1
                )
                if (descriptor.start()) {
                    state.v.compareAndSet(-1, 1)
                } else {
                    if (b.v.value != this) {
                        state.v.compareAndSet(-1, 0)
                        if (state.value == 0) {
                            a.v.compareAndSet(this, expectedA)
                            return false
                        }
                    }
                }
            }
            */
            state.v.compareAndSet(-1, 1)
            if (state.value == 1) {
                a.v.compareAndSet(this, updatedA)
                b.v.compareAndSet(this, updatedB)
                return true
            }
            a.v.compareAndSet(this, expectedA)
            b.v.compareAndSet(this, expectedB)
            return false
        }
    }

    private class RDCSSDescriptor<A, B>(
        val a: Ref<A>, val expectA: A, val updateA: Descriptor,
        val b: Ref<B>, val expectB: B
    ) : Descriptor() {
        private val state: AtomicInt = atomic(-1)

        fun start(): Boolean {
            if (a.value != expectA) {
                state.compareAndSet(-1, 0)
                if (state.value == 1) return complete()
                return false
            }

            if (a.v.compareAndSet(expectA, this)) return complete()
            state.compareAndSet(-1, 0)
            if (state.value == 1) {
                return complete()
            }
            return false
        }
        override fun complete(): Boolean {
            if (b.value != expectB) {
                state.compareAndSet(-1, 0)
                if (state.value == 0) {
                    a.v.compareAndSet(this, expectA)
                    return false
                }
            }
            state.compareAndSet(-1, 1)
            a.v.compareAndSet(this, updateA)
            return true
        }
    }
}