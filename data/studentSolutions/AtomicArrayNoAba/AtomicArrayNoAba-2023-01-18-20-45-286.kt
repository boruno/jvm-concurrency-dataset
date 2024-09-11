import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a: Array<Ref<E>> = Array(size) { Ref(initialValue) }

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    // From lecture
    class Ref<T>(initial: T) {
        val v = atomic<Any?>(initial)

        var value: T
            get() {
                v.loop { cur ->
                    when (cur) {
                        is StrangeDescriptor<*> -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when (cur) {
                        is StrangeDescriptor<*> -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd))
                            return
                    }
                }
            }
    }

    class RDCSSDescriptor<A, B>(
        val a: Ref<A>, val expectA: A, val updateA: A,
        val b: Ref<B>, val expectB: B
    ) {
        fun complete() {
            val update = if (b.value === expectB)
                updateA else expectA
            a.v.compareAndSet(this, update)
        }
    }

    enum class SomeStatus {
        UND, FAIL, SUCC
    }

    class StrangeDescriptor<T>(val refPair: Pair<Ref<T>, Ref<T>>, exceptPair: Pair<T, T>, updatePair: Pair<T, T>
    ) {
        val currStatus = atomic(SomeStatus.UND)

        fun complete() {
//            val update = if (b.value === expectB)
//                updateA else expectA
//            a.v.compareAndSet(this, update)
        }
    }


    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (a[index1].value != expected1 || a[index2].value != expected2) return false


        a[index1].value = update1
        a[index2].value = update2
        return true
    }
}