import kotlinx.atomicfu.*

class AtomicArray<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value

    fun set(index: Int, value: E) {
        a[index].value = value
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        return CAS2Descriptor(Ref(a[index1].value as E), expected1, update1,
            Ref(a[index2].value as E), expected2, update2).complete()
    }

    abstract class Descriptor {
        abstract fun complete() : Boolean
    }

    class DCSSDescriptor(
        val a: Ref<*>, val expectA: Any, val updateA: Any,
        val b: Ref<*>, val expectB: Any) : Descriptor() {
        override fun complete() : Boolean{
            val update = if (b.value === expectB) updateA else expectA
            a.v.compareAndSet(this, update)
            return update == updateA
        }

        fun dcss() : Any? {
            val descriptor = DCSSDescriptor(a, expectA, updateA, b, expectB)
            var r: Any?
            do {
                r = a.getAndCas(expectA, descriptor)
                if (r is DCSSDescriptor) {
                    r.complete()
                }
            } while (r is DCSSDescriptor)
            return r
        }
    }

    class CAS2Descriptor<E : Any>(
        val a: Ref<E>, val expectA: E, val updateA: E,
        val b: Ref<E>, val expectB: E, val updateB: E) : Descriptor(){

        val outcome= Ref(0)
        override fun complete(): Boolean {
            while (true) {
                val resultA = DCSSDescriptor(a, expectA, this, outcome, 0).dcss()
                if (resultA is CAS2Descriptor<*>) {
                    if (resultA != this) {
                        resultA.complete()
                    }
                    continue
                } else {
                    if (resultA != expectA) {
                        outcome.getAndCas(0, -1)
                        break
                    }
                }
                break
            }
            while (true) {
                val resultB = DCSSDescriptor(b, expectB, this, outcome, 0).dcss()
                if (resultB is CAS2Descriptor<*>) {
                    if (resultB != this) {
                        resultB.complete()
                    }
                    continue
                } else {
                    if (resultB != expectB) {
                        outcome.getAndCas(0, -1)
                        break
                    }
                }
                break
            }
            outcome.getAndCas(0, 1)
            a.getAndCas(this, if (outcome.value == 1) updateA else expectA)
            b.getAndCas(this, if (outcome.value == 1) updateB else expectB)

            return outcome.value == 1
        }
    }

    class Ref<E>(initial: E) {
        val v = atomic<Any?>(initial)
        var value: Any?
        get() {
            v.loop { cur ->
                when(cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur
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

        fun getAndCas(expect: Any, update: Any): Any? {
            while (!v.compareAndSet(expect, update)) {
                val cur = v.value
                if (cur != expect) {
                    return cur
                }
            }
            return expect
        }
    }
}