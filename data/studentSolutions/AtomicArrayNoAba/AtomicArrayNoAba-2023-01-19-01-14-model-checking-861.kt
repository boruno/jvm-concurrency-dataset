import kotlinx.atomicfu.*
import kotlin.math.exp

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a: Array<Ref<E>> = Array(size) { Ref(initialValue) }

    class Ref<T>(initial: T) {
        val v = atomic<Any?>(initial)

        fun get(): T {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> {
                        return cur as T
                    }
                }
            }
        }

        fun set(upd: T) {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd))
                        return
                }
            }
        }

    }

    class CASNDescriptor<E : Any>(
        val a: Ref<E>, val expectA: E, val updateA: E,
        val b: Ref<E>, val expectB: E, val updateB: E
    ) : Descriptor() {
        val outcome: AtomicRef<OUTCOME> = atomic(OUTCOME.UNDECIDED)
        override fun complete(): Boolean {
            val dcssResult = b.get() == this || DCSSDescriptor(b,expectB,this).complete()
//            var desc = DCSSDescriptor(
//                b, expectB, this
//            )
//            val bval = b.v.value
//            if (bval == this) {
//
//            } else if (bval is DCSSDescriptor<*>) {
//                desc = bval as DCSSDescriptor<E>
//            } else {
//                b.v.compareAndSet(expectB, desc)
//            }
            if (dcssResult) {
                outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.SUCCESS)
            } else {
                outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.FAILED)
            }
            if (outcome.value == OUTCOME.SUCCESS) {
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
                return true
            }
            if (outcome.value == OUTCOME.FAILED) {
                a.v.compareAndSet(this, expectA)
                b.v.compareAndSet(this, expectB)
                return false
            }
            return false
        }
    }


    class DCSSDescriptor<E : Any?>(
        val a: Ref<E>, val expectA: E, val updateA: CASNDescriptor<*>
    ) : Descriptor() {
        val outcome: AtomicRef<Any?> = atomic(OUTCOME.UNDECIDED)
        override fun complete(): Boolean {
            if(a.v.compareAndSet(expectA,this)){

            }else{
                return false
            }
            if (updateA.outcome.value == OUTCOME.UNDECIDED) {
                outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.SUCCESS)
            } else {
                outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.FAILED)
            }
            if (outcome.value == OUTCOME.FAILED) {
                a.v.compareAndSet(this, expectA)
                return false
            }
            if (outcome.value == OUTCOME.SUCCESS) {
                a.v.compareAndSet(this, updateA)
                return true
            }
            return false
        }
    }

    abstract class Descriptor {
        abstract fun complete(): Boolean
    }

    enum class OUTCOME {
        UNDECIDED, FAILED, SUCCESS
    }

    fun get(index: Int) =
        a[index].get() as Int

    fun cas(index: Int, expected: E, update: E) =
        a[index].v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.

        val desc1 = CASNDescriptor(a[index1], expected1, update1, a[index2], expected2, update2)
        if (a[index1].v.compareAndSet(expected1, desc1)) {
            return desc1.complete()
        } else {
            return false
        }
    }
}