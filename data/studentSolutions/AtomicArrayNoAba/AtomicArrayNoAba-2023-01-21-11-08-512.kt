import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

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

    interface Element<E> {
        fun get() : E
    }

    class Value<E>(val value: E) : Element<E> {
        override fun get(): E {
            return value
        }
    }

    interface Descriptor {
        fun complete()
    }

    class RDCSSDescriptor<A, B> (
        val a: AtomicRef<Element<A>>, val expectA: Element<A>, val updateA: Element<A>,
        val b: AtomicRef<Element<B>>, val expectB: Element<B>
        ) : Descriptor, Element<A> {
            override fun complete() {
                val update = if (b.value === expectB)
                    updateA else expectA
                a.compareAndSet(this, update)
            }

        override fun get(): A {
            complete()
            return a.value.get()
        }
    }
}