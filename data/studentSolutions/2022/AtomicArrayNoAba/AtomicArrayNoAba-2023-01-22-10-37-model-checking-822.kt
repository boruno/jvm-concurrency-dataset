import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int): E {
        return a[index].value!!.value
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

        val aValue: Ref<E> = a[index1].value!!
        val bValue: Ref<E> = a[index2].value!!
        /*
        val aValue: Ref<E>
        val bValue: Ref<E>
        val expectedA: E
        val expectedB: E
        val updateA: E
        val updateB: E
        val indexA: Int
        val indexB: Int
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

         */

        val descriptor = Descriptor(
            aValue, expected1, update1,
            bValue, expected2, update2)

        if (!aValue.v.compareAndSet(expected1, descriptor)) return false
        return descriptor.complete()

        /*
        if (a[index1].value != expected1 || a[index2].value != expected2) return false
        a[index1].value = update1
        a[index2].value = update2
        return true

         */
    }

    private class Ref<T>(initial: T) {
        val v: AtomicRef<Any?> = atomic(initial)

        var value: T
            get() {
                /*
                v.loop { cur ->
                    when(cur) {
                        is Descriptor<*> -> cur.complete()
                        else -> return cur as T
                    }
                }
                 */
                while (true) {
                    val cur = v.value
                    when(cur) {
                        is Descriptor<*> -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when(cur) {
                        is Descriptor<*> -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd)) return
                    }
                }
            }
    }

    private class Descriptor<A>(
        val a: Ref<A>, val expectedA: A, val updatedA: A,
        val b: Ref<A>, val expectedB: A, val updatedB: A
    ) {
        private val state :AtomicRef<String> = atomic("UNDECIDED")
        fun complete(): Boolean {
            while (true) {
                when(state.value) {
                    "UNDECIDED" -> {
                        b.v.compareAndSet(expectedB, this)
                        if (b.v.value == this) {
                            state.compareAndSet("UNDECIDED", "SUCCESS")
                        } else {
                            state.compareAndSet("UNDECIDED", "FAIL")
                        }
                        continue
                    }
                    "FAIL" -> {
                        a.v.compareAndSet(this, expectedA)
                        break
                    }
                    "SUCCESS" -> {
                        a.v.compareAndSet(this, updatedA)
                        b.v.compareAndSet(this, updatedB)
                        break
                    }
                }
            }

            return when(state.value) {
                "SUCCESS" -> true
                else -> false
            }
        }
    }
}

