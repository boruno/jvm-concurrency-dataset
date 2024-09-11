import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {

    companion object {
        fun <E> dcss(
            a: Ref<E>, expectedA: E, updateA: Any?,
            other: CAS2Descriptor<E>
        ): Boolean {
            if (a.v.value == updateA) {
                return false
            }

            val descriptor = RDCSSDescriptor(a, expectedA, updateA, other)
            if (a.cas(expectedA, descriptor)) {
                descriptor.complete()
                return descriptor.status.value == Status.SUCCESSFUL
            } else {
                return false
            }
        }
    }

    private val a = Array(size) { Ref<E?>(initialValue) }

    init {
        for (i in 0 until size) a[i].v.value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].cas(expected, update)

    fun cas2(
        indexA: Int, expectedA: E, updateA: E,
        indexB: Int, expectedB: E, updateB: E
    ): Boolean {
        if (indexA > indexB) {
            return cas2(indexB, expectedB, updateB, indexA, expectedA, updateA)
        }
        val descriptor = CAS2Descriptor(a[indexA], expectedA, updateA, a[indexB], expectedB, updateB)
        if (a[indexA].cas(expectedA, descriptor)) {
            descriptor.complete()
            return descriptor.status.value == Status.SUCCESSFUL
        } else {
            return false
        }
    }

    class Ref<E>(initialValue: E) {
        val v = atomic<Any?>(initialValue)

        var value: E
            get() {
                v.loop { cur ->
                    when (cur) {
                        is Descriptor -> cur.complete()
                        else -> return cur as E
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when (cur) {
                        is Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd))
                            return
                    }
                }
            }

        fun cas(expected: Any?, update: Any?): Boolean {
            v.loop { cur ->
                if (cur is Descriptor) {
                    cur.complete()
                } else if (cur == expected) {
                    if (v.compareAndSet(cur, update)) {
                        return true
                    }
                } else {
                    return false
                }
            }
        }
    }

    interface Descriptor {
        fun complete()
    }

    class RDCSSDescriptor<E>(
        val a: Ref<E>, val expectedA: E, val updateA: Any?,
        val other: CAS2Descriptor<E>
    ) : Descriptor {
        val status: AtomicRef<Status> = atomic(Status.UNDEFINED)

        override fun complete() {
            val newStatus = if (other.status.value == Status.UNDEFINED) {
                Status.SUCCESSFUL
            } else {
                Status.FAILED
            }

            this.status.compareAndSet(Status.UNDEFINED, newStatus)
            val update = if (status.value === Status.SUCCESSFUL) {
                updateA
            } else {
                expectedA
            }
            a.v.compareAndSet(this, update)
        }
    }

    class CAS2Descriptor<T>(
        val a: Ref<T>, val expectA: T, val updateA: T,
        val b: Ref<T>, val expectB: T, val updateB: T,
    ) : Descriptor {

        val status: AtomicRef<Status> = atomic(Status.UNDEFINED)

        override fun complete() {
            if (dcss(b, expectB, this, this)) {
                this.status.compareAndSet(Status.UNDEFINED, Status.SUCCESSFUL)
            } else {
                val newStatus = if (b.v.value != this) {
                    Status.FAILED
                } else {
                    Status.SUCCESSFUL
                }
                this.status.compareAndSet(Status.UNDEFINED, newStatus)
            }

            if (status.value == Status.FAILED) {
                a.cas(this, expectA)
                b.cas(this, expectB)
            } else {
                a.cas(this, updateA)
                b.cas(this, updateB)
            }
        }
    }

    enum class Status {
        UNDEFINED, FAILED, SUCCESSFUL
    }
}