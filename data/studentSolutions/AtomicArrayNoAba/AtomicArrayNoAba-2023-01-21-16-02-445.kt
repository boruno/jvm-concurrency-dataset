import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = arrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i] = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index]!!.get()

    fun cas(index: Int, expected: E, update: E) =
        a[index]!!.cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (index1 == index2) {
            return expected1 == expected2 && update1 == update2 && cas(index1, expected1, update1)
        }
        val refA : Ref<E>
        val refB : Ref<E>
        val expectedA : E
        val expectedB : E
        val updateA : E
        val updateB : E
        if (index1 < index2) {
            refA = a[index1]!!
            refB = a[index2]!!
            expectedA = expected1
            expectedB = expected2
            updateA = update1
            updateB = update2
        } else  {
            refA = a[index2]!!
            refB = a[index1]!!
            expectedA = expected2
            expectedB = expected1
            updateA = update2
            updateB = update1
        }
        val descriptor = CAS2Descriptor(refA, expectedA, updateA, refB, expectedB, updateB)
        return if (refA.v.compareAndSet(expectedA, descriptor)) {
            descriptor.complete()
            descriptor.outcome.get()!!
        } else false
    }
}

class Ref<E>(initial: E) {
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

    fun cas(exp : Any?, upd : Any?) : Boolean {
        v.loop { cur ->
            when(cur) {
                is Descriptor -> cur.complete()
                else -> return v.compareAndSet(exp, upd)
            }
        }
    }
}

interface Descriptor {
    fun complete()
}

class DCSSDescriptor<A, B> (
    val a: Ref<A>, val expectA: A, val updateA: Any,
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

    @Suppress("UNCHECKED_CAST")
    fun <T> get(ref : AtomicRef<Any?>) : T {
        ref.loop { cur ->
            when(cur) {
                is Descriptor -> cur.complete()
                else -> return cur as T
            }
        }
    }
}

class CAS2Descriptor<A, B> (
    val a: Ref<A>, val expectA: A, val updateA: A,
    val b: Ref<B>, val expectB: B, val updateB: B
) : Descriptor
{
    val outcome : Ref<Boolean?> = Ref(null)

    override fun complete() {
        if (b.get() != this) {
            if (dcss(b, expectB, this, outcome, null)) {
                outcome.v.compareAndSet(null, true)
            } else {
                outcome.v.compareAndSet(null, false)
            }
        }
        val update = if (outcome.get()!!) updateA else expectA
        a.v.compareAndSet(this, update)
        if (outcome.get()!!) {
            b.v.compareAndSet(this, updateB)
        }
    }
}

fun <A, B> dcss(ref1: Ref<A>, expected1: A, update1: Any,
                ref2: Ref<B>, expected2: B): Boolean {
    while (true) {
        val descriptor = DCSSDescriptor(ref1, expected1, update1, ref2, expected2)
        return if (ref1.v.compareAndSet(expected1, descriptor)) {
            descriptor.complete()
            descriptor.outcome.get()!!
        } else false
    }
}
