import kotlinx.atomicfu.*
import kotlin.math.exp

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a : Array<Ref<E>> = Array (size) { Ref(initialValue) }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // a multi-word CAS algorithm is used here

        // have to remember the order to avoid

        if (index1 < index2) {
            return performCas(a[index1], expected1, update1, a[index2], expected2, update2)
        } else {
            return performCas(a[index2], expected2, update2, a[index1], expected1, update1)
        }
    }

}

abstract class Descriptor {
    abstract fun complete()
}

class CASNDescriptor<A, B>(
    val a: Ref<A>, val expectA: A, val updateA: A,
    val b: Ref<B>, val expectB: B, val updateB: B,

    ) : Descriptor() {

    val result: AtomicRef<Status> = atomic(Status.Undecided)

    enum class Status {
        Undecided,
        Success,
        Failure
    }

    fun isSuccess(): Boolean {
        return this.result.value == Status.Success
    }

    override fun complete() {
        // check b value, put this descriptor in
        val outcome = if (b.v.value == this || b.compareAndSet(expectB, this)) {
            Status.Success
        } else {
            Status.Failure
        }

        this.result.compareAndSet(Status.Undecided, outcome)

        // clean up
        if (this.result.value == Status.Success) {
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
        } else {
            a.v.compareAndSet(this, expectA)
            b.v.compareAndSet(this, expectB)
        }
    }
}

fun <A, B> performCas(a: Ref<A>, expectA: A, updateA: A, b: Ref<B>, expectB: B, updateB: B): Boolean {
    val casDescriptor = CASNDescriptor(a, expectA, updateA, b, expectB, updateB)

    return if (a.compareAndSet(expectA, casDescriptor)) {
        casDescriptor.complete()
        casDescriptor.isSuccess()
    } else {
        false
    }
}

class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    var value: T
        get() {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur as T
                }
            }
        }
        set(upd) {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd)) return
                }
            }
        }

    // slightly modified set
    fun compareAndSet(expect: Any?, update: Any?): Boolean {
        v.loop { cur ->
            when (cur) {
                is Descriptor -> cur.complete()
                else -> return v.compareAndSet(expect, update)
            }
        }
    }
}

