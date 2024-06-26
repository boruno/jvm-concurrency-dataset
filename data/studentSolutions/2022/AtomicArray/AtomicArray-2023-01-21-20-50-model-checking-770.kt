import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val array = Array(size) { Ref(initialValue) }

    init {
        for (i in 0 until size) array[i].value = initialValue
    }

    fun get(index: Int) =
        array[index].value

    fun set(index: Int, value: E) {
        array[index].value = value
    }

    fun cas(index: Int, expected: E, update: E) =
        array[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2) {
            return if (expected1 == expected2) {
                @Suppress("UNCHECKED_CAST")
                cas(index1, expected1, ((update1 as Int) + 1) as E)
            } else {
                false
            }
        }

        val desc = if (index1 < index2) {
            CASNDescriptor(array[index1], expected1, update1, array[index2], expected2, update2)
        } else {
            CASNDescriptor(array[index2], expected2, update2, array[index1], expected1, update1)
        }.apply { complete() }

        return desc.status.value == Status.SUCCEEDED
    }
}

enum class Status {
    UNDECIDED, SUCCEEDED, FAILED
}

interface Descriptor {
    fun complete()
}

class RDCSSDescriptor<A, B>(
    val a: Ref<A>, val expectA: A, val updateA: Any?,
    val b: Ref<B>, val expectB: B,
) : Descriptor {
    override fun complete() {
        val update = if (b.value === expectB) {
            updateA
        } else {
            expectA
        }
        a.compareAndSet(this, update)
    }

    val value: Any?
        get() {
            var r: Any?

            do {
                r = a.cas1(expectA, this)
                if (r is RDCSSDescriptor<*, *>) {
                    r.complete()
                } else {
                    break
                }
            } while (true)

            if (r == expectA) {
                complete()
            }

            return r
        }
}

class CASNDescriptor<A, B>(
    val a: Ref<A>, val expectedA: A, val updateA: A,
    val b: Ref<B>, val expectedB: B, val updateB: B,
) : Descriptor {
    val status = Ref(Status.UNDECIDED)

    override fun complete() {
        while (status.value == Status.UNDECIDED) {
            var value: Any?

            // a
            do {
                value = RDCSSDescriptor(a, expectedA, this, status, Status.UNDECIDED).value

                if (value is Descriptor && value !== this) {
                    value.complete()
                } else {
                    break
                }
            } while (true)

            if (value !is Descriptor && value !== expectedA) {
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                break
            }

            // b
            do {
                value = RDCSSDescriptor(b, expectedB, this, status, Status.UNDECIDED).value

                if (value is Descriptor && value !== this) {
                    value.complete()
                } else {
                    break
                }
            } while (true)

            if (value !is Descriptor && value !== expectedB) {
                status.compareAndSet(Status.UNDECIDED, Status.FAILED)
                break
            }

            status.compareAndSet(Status.UNDECIDED, Status.SUCCEEDED)
        }

        val succeeded = status.value == Status.SUCCEEDED

        a.compareAndSet(this, if (succeeded) updateA else expectedA)
        b.compareAndSet(this, if (succeeded) updateB else expectedB)
    }
}

class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    @Suppress("UNCHECKED_CAST")
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
                when {
                    cur is Descriptor -> cur.complete()
                    v.compareAndSet(cur, upd) -> return
                }
            }
        }

    fun compareAndSet(expect: Any?, update: Any?): Boolean {
        return v.compareAndSet(expect, update)
    }

    fun cas1(expect: Any?, update: Any?): Any? {
        v.loop { cur ->
            if (cur != expect) {
                return cur
            } else {
                if (v.compareAndSet(cur, update)) {
                    return cur
                }
            }
        }
    }
}