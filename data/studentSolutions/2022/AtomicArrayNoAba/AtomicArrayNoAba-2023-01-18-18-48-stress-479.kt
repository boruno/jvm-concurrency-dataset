import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val refs = Array<Ref<E>>(size) { i -> Ref(initialValue) }

    // init {
    //     for (i in 0 until size) refs[i].value = Ref(initialValue)
    // }

    fun get(index: Int) =
        refs[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        refs[index].v.compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        return cas2ref(refs[index1], expected1, update1,
                       refs[index2], expected2, update2)
        // if (index1 == index2) {
            // return cas(index1, expected1, ((expected1 as Int) + 2) as E)
            // return cas(index1, expected1, expected1 + 2)
        // }
    }

    fun <A,B> cas2ref(a: Ref<A>, expectA: A, updateA: A,
                      b: Ref<B>, expectB: B, updateB: B): Boolean {
        val desc = CASNDescriptor(a, expectA, updateA, b, expectB, updateB)
        if (!a.v.compareAndSet(expectA, desc)) return false
        if (!dcss(b, expectB, updateB, desc.outcome, 0)) {
            return false
        }
        a.value = updateA
        // b.value = updateB
        return desc.outcome.v.value == 1


    }
        // if (a.value == expectA && b.value == expectB) {
        //     a.value = updateA
        //     b.value = updateB
        //     true
        // } else {
        //     false
        // }

    fun <A,B> dcss(a: Ref<A>, expectA: A, updateA: A,
                   b: Ref<B>, expectB: B): Boolean {
        val desc = RDCSSDescriptor(a, expectA, updateA, b, expectB)
        if (!a.v.compareAndSet(expectA, desc)) return false
        a.value = updateA
        return desc.outcome.v.value == 1
    }
}

abstract class Descriptor {
   abstract fun complete()
}

class RDCSSDescriptor<A, B>(
    val a: Ref<A>, val expectA: A, val updateA: A,
    val b: Ref<B>, val expectB: B
) : Descriptor() {
    val outcome = Ref(0) // 1 - success, 2 - fail

    override fun complete() {
        val update = if (b.value === expectB) updateA else expectA
        if (a.v.compareAndSet(this, update)) {
            outcome.v.compareAndSet(0, 1)
        } else {
            outcome.v.compareAndSet(0, 2)
        }
    }
}
class CASNDescriptor<A, B>(
    val a: Ref<A>, val expectA: A, val updateA: A,
    val b: Ref<B>, val expectB: B, val updateB: B
) : Descriptor() {
    val outcome = Ref(0) // 1 - success, 2 - fail

    override fun complete() {
        val update = if (b.value === expectB) updateA else expectA
        if (a.v.compareAndSet(this, update)) {
            outcome.v.compareAndSet(0, 1)
        } else {
            outcome.v.compareAndSet(0, 2)
        }
    }
}

class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    var value: T
        get() {
            v.loop { cur ->
                if (cur is Descriptor) cur.complete()
                else return cur as T
            }
        }
        set(upd) {
            v.loop { cur ->
                if (cur is Descriptor) cur.complete()
                else if (v.compareAndSet(cur, upd)) return
            }
        }
}
