import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) {
            a[i].value = Ref(initialValue)
        }
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.v.compareAndSet(expected, update)

    fun dcss(index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E): Boolean {
        val desc = DCSSDescriptor(a[index1].value, expected1, update1, a[index2].value, expected2)
        if (a[index1].value!!.v.compareAndSet(expected1, desc)) {
            if (a[index2].value == expected2) {
                a[index1].value!!.v.compareAndSet(desc, update1)
                return true
            } else {
                a[index1].value!!.v.compareAndSet(desc, expected1)
                return false
            }
        } else {
            return false
        }
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        var o: String
        var desc = DCSSDescriptor(a[index1].value, expected1, update1, a[index2].value, expected2)
        if(a[index1].value!!.v.compareAndSet(expected1, desc)) {
            if(this.dcss(index2, expected2, update2, index1, expected1)) {
                o = "SUCC"

            } else {
                o = "FAIL"
            }
        }
        else {
            o = "FAIL"
        }
        return o == "SUCC"
    }


}

abstract class Desc {
    abstract fun complete()
}

@Suppress("UNCHECKED_CAST")
class Ref<E>(initial: E) {
    val v = atomic<Any?>(initial)

    var value: E
        get()  {
            v.loop { cur ->
                when(cur) {
                    is Desc -> cur.complete()
                    else -> return cur as E
                }
            }
        }
        set(upd) {
            v.loop { cur ->
                when(cur) {
                    is Desc -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd))
                        return
                }
            }
        }

}

class DCSSDescriptor<E>(
    val a: Ref<E>?, val expected1: E, val update1: E,
    val b: Ref<E>?, val expected2: E
): Desc() {
    override fun complete() {
        val update = if (b!!.value === expected2)
            update1 else expected1
        a!!.v.compareAndSet(this, update)
    }
}

