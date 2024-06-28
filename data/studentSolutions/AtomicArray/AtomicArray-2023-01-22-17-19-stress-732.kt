import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {

    private sealed interface Outcome
    private object Undecided : Outcome
    private object Success : Outcome
    private object Fail : Outcome

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
                        else -> return cur as T
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
                    else -> {
                        if (cur != expect) return false
                        else if (v.compareAndSet(expect, update)) return true
                    }
                }
            }
        }
    }

    private class RDCSSDescriptor<A, B>(
        val a: Ref<A>, val expectedA: A, val updateA: Any,
        val b: Ref<B>, val expectedB: B,
    ) : Descriptor {
        override var status = Ref<Outcome>(Undecided)

        override fun prepare(): Boolean = a.v.compareAndSet(expectedA, this).also {
            val memorized = a.v.value

            if (!it) {
                if (memorized !is Descriptor) {
                    if (memorized == expectedA) {
                        this.prepare()
                        return@also
                    }
                    status.compareAndSet(Undecided, Fail)
                } else {
                    if (memorized == this) {
                        status.compareAndSet(Undecided, Success)
                        return@also
                    }

                    memorized.complete()
                }
            }
        }

        override fun complete() {
            when (status.value) {
                is Success -> a.v.compareAndSet(this, updateA)
                is Fail -> a.v.compareAndSet(this, expectedA)
                is Undecided -> {
                    status.compareAndSet(Undecided, if (b.value == expectedB) Success else Fail)

                    complete()
                }
            }
        }
    }

    private class Cas2Descriptor<A, B>(
        val a: Ref<A>, val expectedA: A, val updateA: A,
        val b: Ref<B>, val expectedB: B, val updateB: B,
    ) : Descriptor {
        override var status = Ref<Outcome>(Undecided)

        override fun prepare(): Boolean = a.v.compareAndSet(expectedA, this).also {
            if (!it) {
                status.compareAndSet(Undecided, Fail)
            }
        }

        override fun complete() {
            when (status.value) {
                is Success -> {
                    b.v.compareAndSet(this, updateB)
                    a.v.compareAndSet(this, updateA)
                }
                is Fail -> {
                    b.v.compareAndSet(this, expectedB)
                    a.v.compareAndSet(this, expectedA)
                }
                is Undecided -> {
                    val dcss = RDCSSDescriptor(b, expectedB, this, status, Undecided)
                    if (dcss.prepare()) {
                        dcss.complete()
                    }

                    status.compareAndSet(Undecided, dcss.status.value)

                    complete()
                }
            }
        }
    }

    private val a = Array<Ref<E>>(size) { Ref(initialValue) }

    init {
        for (i in 0 until size) a[i] = Ref(initialValue)
    }

    fun get(index: Int): E =
        a[index].value

    fun set(index: Int, value: E) {
        a[index].value = value
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        val a = this.a[index1]
        val b = this.a[index2]

        val cas2 = Cas2Descriptor(a, expected1, update1, b, expected2, update2)
        if (cas2.prepare()) {
            cas2.complete()
        }

        return cas2.status.value == Success
    }
}
