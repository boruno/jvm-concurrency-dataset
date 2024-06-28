import kotlinx.atomicfu.*

class AReft<E>(initialValue: E) {
    val x = atomic<Any?>(initialValue)

    fun cas(exp: Any?, upd: Any?): Boolean {
        while (true) {
            val c = x.value
            when (c) {
                is Resolver<*> -> c.complete()
                exp -> {
                    val res = x.compareAndSet(c, upd)
                    if (res) {
                        return true
                    }
                }
                else -> return false
            }
        }
    }
    var value: E
        get() {
            while (true) {
                val c = x.value
                val e = c as E
                when (c) {
                    is Resolver<*> -> c.complete()
                    else -> c as E
                }
            }
        }
        set(value) {
            while (true) {
                val c = x.value
                when (c) {
                    is Resolver<*> -> c.complete()
                    else -> if (x.compareAndSet(c, value)) return
                }
            }
        }
}

sealed interface State
object Process : State
object Success : State
object Error : State


interface Resolver<E> {
    fun complete()
}

class CsCRResolver<E>(
    private val r1: AReft<E>, private val expected1: E, private val update1: Any?,
    private val nextResolver: CasResolver<E>
) : Resolver<E> {
    val cState: AtomicRef<State> = atomic(Process)

    override fun complete() {
        val v = nextResolver.cellStatus.value
        var out: State = Error
        if (v == Process) {
            out = Success
        }

        cState.compareAndSet(Process, out)
        var upd = update1
        if (cState.value !== Success) {
            upd = expected1
        }
        val comp = r1.x
        comp.compareAndSet(this, upd)
    }
}

class CasResolver<E>(
    private val r1: AReft<E>, private val exp1: E, private val upd1: E,
    private val r2: AReft<E>, private val exp2: E, private val upd2: E
) : Resolver<E> {
    val cellStatus: AtomicRef<State> = atomic(Process)


    override fun complete() {
        val d = CsCRResolver(r2, exp2, this, this)
        val i =  r2.x.value?.equals(this) ?: false || if (r2.cas(exp2, d)) {
            d.complete()
            d.cState.value == Success
        } else {
            false
        }

        if (i == true) {
            this.cellStatus.compareAndSet(Process, Success)
        } else {
            val outcome: State
            if (r2.x.value != this) {
                outcome = Error
            } else {
                outcome = Success
            }
            this.cellStatus.compareAndSet(Process, outcome)
        }

        var p = Pair(exp1, exp2)
        if (this.cellStatus.value != Error) {
            p = Pair(upd1, upd2)
        }

        r1.x.compareAndSet(this, p.first)
        r2.x.compareAndSet(this, p.second)
    }
}

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = arrayOfNulls<AReft<E>>(size)

    init {
        (0 until size).forEach { i ->
            a[i] = AReft(initialValue)
        }
    }

    fun get(index: Int): E = a[index]!!.value

    fun cas(index: Int, expected: E, update: E): Boolean = a[index]?.cas(expected, update) ?: false

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2) {
            val expected1Int = ((expected1 as Int) + 2) as E
            return cas(index1, expected1, expected1Int)
        }


        val (index, expected, desc) = getValues(index1, expected1, update1, index2, expected2, update2)
        if (a[index]!!.cas(expected, desc)) {
            desc.complete()
            return desc.cellStatus.value == Success
        }

        return false
    }

    fun getValues(indx1: Int, expected1: E, update1: E,
                  indx2: Int, expected2: E, update2: E): Triple<Int, E, CasResolver<E>> {
        val res: Triple<Int, E, CasResolver<E>>
        if (indx1 > indx2) {
            res = Triple(indx2, expected2, CasResolver(a[indx2]!!, expected2, update2, a[indx1]!!, expected1, update1))
        } else {
            res = Triple(indx1, expected1, CasResolver(a[indx1]!!, expected1, update1, a[indx2]!!, expected2, update2))
        }
        return res
    }
}