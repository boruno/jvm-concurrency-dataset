import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
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

    fun cas(index: Int, expected: Any, update: Any): Boolean {
        while (!a[index].compareAndSet(expected, update)) {
            val cur = get(index)
            if (cur != expected) {
                return false
            }
        }
        return true
    } //todo

    fun getAndCas(index: Int, expect: Any, update: Any): Any? {
        do {
            val cur = a[index].value
            if (cur != expect) {
                return cur
            }
        } while (!a[index].compareAndSet(expect, update))
        return expect
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (index1 == index2) {
            if (expected1 != expected2) {
                return false
            }
            if (update1 == update2) {
                return cas(index1, expected1, update1) && cas(index1, expected2, update2)
            }
        }
        val result = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1,
                index2, expected2, update2, this).complete()
        } else {
            CAS2Descriptor(index2, expected2, update2,
                index1, expected1, update1, this).complete()
        }

        return result
    }

    abstract class Descriptor {
        abstract fun complete() : Boolean
    }

    class DCSSDescriptor<E : Any>(
        val index1: Int, val expect1: Any, val update1: Any, val array: AtomicArrayNoAba<E>,
        val b: Ref<Int>, val expect2: Any) : Descriptor() {
        override fun complete() : Boolean{
            if (b.value == expect2) {
                array.cas(index1, this, update1)
                return true
            } else {
                array.cas(index1, this, expect1)
                return false
            }
        }

        fun dcss() : Any? {
            var r: Any?
            do {
                r = array.getAndCas(index1, expect1, this)
                if (r is DCSSDescriptor<*>) {
                    r.complete()
                }
            } while (r is DCSSDescriptor<*>)
            if (r == expect1) {
                complete()
            }
            return r
        }
    }

    class CAS2Descriptor<E : Any>(
        val index1: Int, val expect1: E, val update1: E,
        val index2: Int, val expect2: E, val update2: E,
        val array: AtomicArrayNoAba<E>
    ) : Descriptor(){

        val outcome = Ref(0)
        override fun complete(): Boolean {
            if (outcome.value == 0) {
                while (true) {
                    val result1 = DCSSDescriptor(index1, expect1, this, array, outcome, 0).dcss()
                    if (result1 is CAS2Descriptor<*>) {
                        if (result1 != this) {
                            result1.complete()
                            continue
                        }
                        break
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
                    if (result2 is CAS2Descriptor<*>) {
                        if (result2 != this) {
                            result2.complete()
                            continue
                        }
                        break
                    } else {
                        if (result2 != expect2) {
                            outcome.getAndCas(0, -1)
                            break
                        }
                    }
                    break
                }
            }

            outcome.getAndCas(0, 1)

            array.cas(index2, this, if (outcome.value == 1) update2 else expect2)
            array.cas(index1, this, if (outcome.value == 1) update1 else expect1)
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
            do {
                val cur = v.value
                if (cur != expect) {
                    return cur
                }
            } while (!v.compareAndSet(expect, update))
            return expect
        }
    }
}