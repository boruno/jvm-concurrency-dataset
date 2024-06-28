import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (a[index1].value != expected1 || a[index2].value != expected2) return false
        a[index1].value = update1
        a[index2].value = update2
        return true
    }

    fun <A, B> rdcss(
        indexA: Int, expectedA: A, updateA: A,
        indexB: Int, expectedB: B
    ): Boolean {

        val descriptor = RDCSSDescriptor(
            a[indexA] as Ref<A>,
            expectedA,
            updateA,
            a[indexB] as Ref<B>,
            expectedB
        )

        if (a[indexA].v.compareAndSet(expectedA, descriptor)) {
            if (a[indexB].value == expectedB) {
                descriptor.outcome.compareAndSet(Status.UNDECIDED, Status.SUCCEDED)
                a[indexA].v.compareAndSet(descriptor, updateA)
                return true
            } else {
                descriptor.outcome.compareAndSet(Status.UNDECIDED, Status.FAILED)
                a[indexA].v.compareAndSet(descriptor, expectedA)
                return true

            }
        } else {
            return false
        }
    }


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


    class RDCSSDescriptor<A, B>(
        val a: Ref<A>,
        val expectA: A,
        val updateA: A,
        val b: Ref<B>,
        val expectB: B
    ) : Descriptor() {

        val outcome: AtomicRef<Status> = atomic(Status.UNDECIDED)

        override fun complete() {
            val update = if (b.value === expectB) {
                updateA
            } else {
                expectA
            }
            a.v.compareAndSet(this, update)
        }
    }

//    class CASNDescriptor<A, B>(
//        val status: Status,
//        val a: Ref<A>, val expectA: A, val updateA: A,
//        val b: Ref<B>, val expectB: B, val updateB: B
//    ) : Descriptor() {
//        override fun complete() {
//
//        }
//    }

    enum class Status {
        UNDECIDED, FAILED, SUCCEDED
    }
}