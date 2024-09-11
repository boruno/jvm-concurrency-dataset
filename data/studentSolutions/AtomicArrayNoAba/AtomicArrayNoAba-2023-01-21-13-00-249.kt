import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int) : E {
        val ref = a[index]
        ref.loop { cur ->
            when(cur) {
                is Descriptor -> cur.complete()
                else -> return cur as E
            }
        }
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (a[index1].value != expected1 || a[index2].value != expected2) return false
        a[index1].value = update1
        a[index2].value = update2
        return true
    }

    /*fun dcss(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E): Boolean {
        while (true) {
            val element1 = a[index1].value!!

            if (element1.get() == expected1) {
                val descriptor = DCSSDescriptor(element1, expected1, update1, a[index2].value!!, expected2)
                element1.set(descriptor)
                descriptor.complete()
                return descriptor.outcome.get()!!
            }
        }
    }*/
    /*@Suppress("UNCHECKED_CAST")
    fun get(ref : AtomicRef<Any?>): E {
        ref.loop { cur ->
            when(cur) {
                is Descriptor -> cur.complete()
                else -> return cur as E
            }
        }
    }*/

    /*class Ref<E>(initial: E) {
        val v = atomic<Any?>(initial)

        @Suppress("UNCHECKED_CAST")
        fun get() : E {
            v.loop { cur ->
                when(cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur as E
                }
            }
        }

        fun set(upd : Any?) {
            v.loop { cur ->
                when(cur) {
                    is Descriptor -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd))
                        return
                }
            }
        }
    }*/

    /*fun <E> wrapDescriptor(descriptor: Descriptor) {
        val ref = Ref<E>()
    }*/

    interface Descriptor {
        fun complete()
    }

    /*class DCSSDescriptor<A, B> (
        val a: Ref<A>, val expectA: A, val updateA: A,
        val b: Ref<B>, val expectB: B
    ) : Descriptor
    {
        val outcome : Ref<Boolean?> = Ref(null)

        override fun complete() {
            if (outcome.get() == null) {
                if (b.get() === expectB) {
                    outcome.v.compareAndSet(null, true)
                } else {
                    outcome.v.compareAndSet(null, false)
                }
            }
            val update = if (outcome.get()!!) updateA else expectA
            a.v.compareAndSet(this, update)
        }
    }*/


    /*open class Element<E> {
    }

    class Value<E>(val value: E) : Element<E>() {
        fun get(): E {
            return value
        }
    }*/



    /*class CAS2Descriptor<A, B> (
        val a: AtomicRef<Element<A>?>, val expectA: Value<A>, val updateA: Value<A>,
        val b: AtomicRef<Element<B>?>, val expectB: Value<B>, val updateB: Value<B>
        ) : Descriptor, Element<A>, Element<B> {
        val outcome : AtomicRef<Boolean?> = atomic(null)

        override fun complete() {
            if (outcome.value == null) {
                if (b.value!!.get() === expectB.get()) {
                    outcome.compareAndSet(null, true)
                } else {
                    outcome.compareAndSet(null, false)
                }
            }
            val update = if (outcome.value!!) updateA else expectA
            a.compareAndSet(this, update)
        }

        override fun get(): A {
            complete()
            return a.value!!.get()
        }

        override fun get(): B {
            complete()
            return b.value!!.get()
        }
    }*/

    /*class DCSSDescriptor<A, B> (
        val a: AtomicRef<Element<A>?>, val expectA: Value<A>, val updateA: Value<A>,
        val b: AtomicRef<Element<B>?>, val expectB: Value<B>
    ) : Descriptor, Element<A> {
        val outcome : AtomicRef<Boolean?> = atomic(null)

        override fun complete() {
            if (outcome.value == null) {
                if (b.value!!.get() === expectB.get()) {
                    outcome.compareAndSet(null, true)
                } else {
                    outcome.compareAndSet(null, false)
                }
            }
            val update = if (outcome.value!!) updateA else expectA
            a.compareAndSet(this, update)
        }

        override fun get(): A {
            complete()
            return a.value!!.get()
        }
    }*/
}