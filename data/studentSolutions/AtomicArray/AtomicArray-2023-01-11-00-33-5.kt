import kotlinx.atomicfu.*

class AtomicArray<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any?>(size)

    // todo убрать Ref, вместо него везде передавать сам AtomicArray и индексы
    // get и set сделать как в Ref

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        a[index].loop { cur ->
            when(cur) {
                is Descriptor -> cur.complete()
                else -> return cur as E
            }
        }
    }

    fun set(index: Int, value: Any) {
        a[index].loop { cur ->
            when(cur) {
                is Descriptor -> cur.complete()
                else -> if (a[index].compareAndSet(cur, value)) return
            }
        }
    }

    fun cas(index: Int, expected: Any, update: Any) =
        a[index].compareAndSet(expected, update)

    fun getAndCas(index: Int, expect: Any, update: Any): Any? {
        while (!a[index].compareAndSet(expect, update)) {
            val cur = a[index].value
            if (cur != expect) {
                return cur
            }
        }
        return expect
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        val result = CAS2Descriptor(index1, expected1, update1,
            index2, expected2, update2, this).complete()
        return result
    }

    abstract class Descriptor {
        abstract fun complete() : Boolean
    }

    class DCSSDescriptor<E : Any>(
        val indexA: Int, val expectA: Any, val updateA: Any, val array: AtomicArray<E>,
        val b: Ref<Int>, val expectB: Any) : Descriptor() {
        override fun complete() : Boolean{
            val update = if (b.value == expectB) updateA else expectA
            array.cas(indexA, this, update)
            return update == updateA
        }

        fun dcss() : Any? {
            var r: Any?
            do {
                r = array.getAndCas(indexA, expectA, this)
                if (r is DCSSDescriptor<*>) {
                    r.complete()
                }
            } while (r is DCSSDescriptor<*>)
            return r
        }
    }

    class CAS2Descriptor<E : Any>(
        val index1: Int, val expect1: E, val update1: E,
        val index2: Int, val expect2: E, val update2: E,
        val array: AtomicArray<E>) : Descriptor(){

        val outcome= Ref(0)
        override fun complete(): Boolean {
            while (true) {
                val result1 = DCSSDescriptor(index1, expect1, this, array, outcome, 0).dcss()
                if (result1 is Descriptor) {
                    if (result1 != this) {
                        result1.complete()
                    }
                    continue
                } else {
                    if (result1 != expect1) {
                        outcome.getAndCas(0, -1)
                        break
                    }
                }
                break
            }
            while (true) {
                val result2 = DCSSDescriptor(index2, expect2, this, array, outcome, 0).dcss()
                if (result2 is Descriptor) {
                    if (result2 != this) {
                        result2.complete()
                    }
                    continue
                } else {
                    if (result2 != expect2) {
                        outcome.getAndCas(0, -1)
                        break
                    }
                }
                break
            }
            outcome.getAndCas(0, 1)

            array.cas(index1, expect1, if (outcome.value == 1) update1 else expect1)
            array.cas(index2, expect1, if (outcome.value == 1) update1 else expect1)
            return outcome.value == 1
        }
    }

    class Ref<E>(initial: E) {
        val v = atomic<Any?>(initial)
        var value: E
            get() {
                v.loop { cur ->
                    when(cur) {
                        is Descriptor -> cur.complete()
                        else -> return cur as E
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