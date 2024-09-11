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
        if (a[index1]!!.get() != expected1 || a[index2]!!.get() != expected2) return false
        a[index1]!!.set(update1)
        a[index2]!!.set(update2)
        return true
    }






    /*fun dcss(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E): Boolean {
        return dcss(a[index1], expected1, update1, a[index2], expected2)
    }*/
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

@Suppress("UNCHECKED_CAST")
fun <T> get(ref : AtomicRef<*>) : T {
    ref.loop { cur ->
        when(cur) {
            is Descriptor -> cur.complete()
            else -> return cur as T
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
