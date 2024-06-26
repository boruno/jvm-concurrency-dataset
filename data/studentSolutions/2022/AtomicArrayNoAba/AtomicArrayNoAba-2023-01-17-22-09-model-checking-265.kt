import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a: Array<Ref<E>> = Array(size) { Ref(initialValue) }

    abstract class Descriptor {
        abstract fun complete()
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
                        else -> if (v.compareAndSet(cur, upd))
                            return
                    }
                }
            }
    }

    enum class Status { UNDEFINED, SUCCESS, FAIL }

    class CASDescriptor<E>(
        val a: Ref<E>, val expectA: E, val updateA: E,
        val b: Ref<E>, val expectB: E, val updateB: E,
        val outcome: AtomicRef<Status> = atomic(Status.UNDEFINED)
    ) : Descriptor() {
        override fun complete() {
            while (outcome.value == Status.UNDEFINED) {
                if (b.value === expectB) {
                    b.v.compareAndSet(expectB, this)
                    outcome.compareAndSet(Status.UNDEFINED, Status.SUCCESS)
                } else
                    outcome.compareAndSet(Status.UNDEFINED, Status.FAIL)
            }
            when (outcome.value) {
                Status.SUCCESS -> {
                    a.value = updateA
                    a.value = updateB
                }
                Status.FAIL -> {
                    a.value = expectA
                }
                else -> {}
            }
        }
    }

    fun get(index: Int) = a[index].value

    fun cas(index: Int, expected: E, update: E) =
        a[index].v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2)
            return if (expected1 == expected2)
                cas(index1, expected1, update2)
            else
                false

        val minRef: Ref<E>; val maxRef: Ref<E>
        val minExpected: E; val maxExpected: E
        val minUpdate:   E; val maxUpdate: E
        if (index1 <= index2) {
            minRef = a[index1]
            maxRef = a[index2]
            minExpected = expected1
            maxExpected = expected2
            minUpdate = update1
            maxUpdate = update2
        } else {
            minRef = a[index2]
            maxRef = a[index1]
            minExpected = expected2
            maxExpected = expected1
            minUpdate = update2
            maxUpdate = update1
        }
        while (true) {
            val desk = CASDescriptor(minRef, minExpected, minUpdate, maxRef, maxExpected, maxUpdate)
            if (expected1 == a[index1].v.value) {
                a[index1].v.compareAndSet(expected1, desk)
                desk.complete()
                return desk.outcome.value == Status.SUCCESS
            } else {
                return false
            }
        }
    }
}