import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2) {
            return cas(index1, expected1, (((expected1) as Int) + 2) as E)
        }
        val descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)

        if (a[index1].value!!.value == expected1) {
            a[index1].value!!.v.compareAndSet(expected1, descriptor)
            descriptor.complete()
        } else {
            descriptor.outcome.value = "FAILED"
        }

        return descriptor.outcome.value == "SUCCESS"

    }

    abstract inner class Descriptor{
        abstract fun complete() : Boolean
    }

    inner class RDCSSDescriptor(
        val desc: CAS2Descriptor, val bind: Int
    ) : Descriptor() {
        val outcome = atomic("UNDECIDED")
        override fun complete() : Boolean {
            if (desc.outcome.value == "UNDECIDED") {
                this.outcome.compareAndSet("UNDECIDED", "SUCCESS")
                a[bind].value!!.v.compareAndSet(this, desc)
                return true
            } else {
                return false
            }
        }
    }

    inner class CAS2Descriptor(
        val aindx: Int, val expectA: E, val updateA: E,
        val bindx: Int, val expectB: E, val updateB: E) : Descriptor() {
        val rdcssDescriptor = RDCSSDescriptor(this, bindx)
        val outcome = atomic("UNDECIDED")
        override fun complete() : Boolean {

            if (a[bindx].value!!.v.value != this) {
                if (a[bindx].value!!.v.compareAndSet(expectB, rdcssDescriptor)) {
                    rdcssDescriptor.complete()
                } else {
                    if (a[bindx].value!!.v.value == rdcssDescriptor) {
                        rdcssDescriptor.complete()
                    } else {
                        rdcssDescriptor.outcome.compareAndSet("UNDECIDED", "FAILED")
                    }
                }
            }

            if (rdcssDescriptor.outcome.value == "SUCCESS") {
                this.outcome.compareAndSet("UNDECIDED", "SUCCESS")
                a[aindx].value!!.v.compareAndSet(this, updateA)
                a[bindx].value!!.v.compareAndSet(this, updateB)
                return true
            } else {
                this.outcome.compareAndSet("UNDECIDED", "FAILED")
                a[aindx].value!!.v.compareAndSet(this, expectA)
                return false
            }
        }

    }

    inner class Ref(initial: E) {
        val v: AtomicRef<Any?> = atomic(initial)

        var value: E
            get() {
                v.loop { cur ->
                    when (cur) {
                        is AtomicArrayNoAba<*>.Descriptor -> cur.complete()
                        else -> return cur as E
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when (cur) {
                        is AtomicArrayNoAba<*>.Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd)) return
                    }
                }
            }
    }

}