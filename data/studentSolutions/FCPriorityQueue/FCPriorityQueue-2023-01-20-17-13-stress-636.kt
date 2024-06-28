import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val isLocked = atomic(false)
    private fun tryLock() = isLocked.compareAndSet(expect = false, update = true)
    private fun unlock() { isLocked.getAndSet(false) }

    private val SIZE = 10
    private val fcArray = atomicArrayOfNulls<Any?>(SIZE)

    internal class Op<E>(private val op: () -> E?) {
        operator fun invoke(): Res<E> {
            return Res(op())
        }
    }

    internal class Res<E>(val value: E?) {
        operator fun invoke(): Res<E> {
            return this
        }
    }

    private fun randIdx(): Int {
        return ThreadLocalRandom.current().nextInt(SIZE)
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return combine(Op(q::poll))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return combine(Op(q::peek))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        combine(Op(fun(): E? {q.add(element); return null}))
    }

    private fun combine(op: Op<E>): E? {
        var idx: Int
        do {
            idx = randIdx()
        }
        while (!fcArray[idx].compareAndSet(null, op))

        while (true) {
            if (tryLock()) {
                try {
                    for (i in 0 until SIZE) {
                        val curOp = fcArray[i].value
                        if (curOp is Op<*>) {
                            fcArray[i].getAndSet(curOp())
                        }
                    }
                    val res = fcArray[idx].getAndSet(null) as Res<E>
                    return res.value
                } finally { // никогда не упадёт, но мы типо энтерпрайз код пишем
                    unlock()
                }
            } else {
                val res = fcArray[idx].value
                if (res is Res<*>) {
                    fcArray[idx].getAndSet(null)
                    return res.value as E
                }
            }
        }
    }
}