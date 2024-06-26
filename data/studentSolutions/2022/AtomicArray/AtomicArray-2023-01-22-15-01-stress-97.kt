import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {

    private sealed interface Outcome
    private object UNDECIDED : Outcome
    private object SUCCESS : Outcome
    private object FAIL : Outcome

    private sealed interface Descriptor {
        fun prepare(): Boolean
        fun complete()
        var status: Ref<Outcome>
    }

    // private sealed interface Either<A, B>
    // private data class Left<A, B>(a: A) : Either<A, B>
    // private data class Right<A, B>(b: B) : Either<A, B>

    @Suppress("UNCHECKED_CAST")
    private class Ref<T>(init: T) {
        // val v = atomic<Either<T, Descriptor>(init)
        val v = atomic<Any?>(init)

        var value: T
            get() {
                v.loop { cur ->
                    when (cur) {
                        is Descriptor -> cur.complete()
                        else -> v.value as T
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when (cur) {
                        is Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd)) {
                            return
                        }
                    }
                }
            }

        fun <A, B> compareAndSet(expect: A, update: B): Boolean {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> return v.compareAndSet(expect, update)
                }
            }
        }
    }

    private class RDCSSDescriptor<A, B>(
        val a: Ref<A>, val expectedA: A, val updateA: Any,
        val b: Ref<B>, val expectedB: B,
    ) : Descriptor {
        override var status = Ref<Outcome>(UNDECIDED)

        override fun prepare(): Boolean = a.compareAndSet(expectedA, this).also {
            if (!it) status.compareAndSet(UNDECIDED, FAIL)
        }

        override fun complete() {
            when (status.value) {
                is SUCCESS -> a.v.compareAndSet(this, updateA)
                is FAIL -> a.v.compareAndSet(this, expectedA)
                is UNDECIDED -> {
                    status.compareAndSet(UNDECIDED, if (b.value == expectedB) SUCCESS else FAIL)

                    complete()
                }
            }
        }
    }

    private class Cas2Descriptor<A, B>(
        val a: Ref<A>, val expectedA: A, val updateA: A,
        val b: Ref<B>, val expectedB: B, val updateB: B,
    ) : Descriptor {
        override var status = Ref<Outcome>(UNDECIDED)

        override fun prepare(): Boolean = a.compareAndSet(expectedA, this).also {
            if (!it) status.compareAndSet(UNDECIDED, FAIL)
        }

        override fun complete() {
            when (status.value) {
                is SUCCESS -> {
                    a.v.compareAndSet(this, updateA)
                    b.v.compareAndSet(this, updateB)
                }
                is FAIL -> {
                    a.v.compareAndSet(this, expectedA)
                    b.v.compareAndSet(this, expectedB)
                }
                is UNDECIDED -> {
                    val dcss = RDCSSDescriptor(b, expectedB, this, status, UNDECIDED)
                    dcss.prepare()
                    dcss.complete()

                    status.compareAndSet(UNDECIDED, dcss.status.value)

                    complete()
                }
            }
        }
    }

    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun set(index: Int, value: E) {
        a[index].value!!.value = value
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        val a = this.a[index1].value!!
        val b = this.a[index2].value!!

        val cas2 = Cas2Descriptor(a, expected1, update1, b, expected2, update2)
        cas2.prepare()
        cas2.complete()

        return cas2.status.value == SUCCESS
    }
}
