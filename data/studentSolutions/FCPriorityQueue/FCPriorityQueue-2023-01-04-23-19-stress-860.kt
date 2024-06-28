import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Function

@Suppress("UNCHECKED_CAST")
class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val fc_array = atomicArrayOfNulls<Any>(FC_ARRAY_SIZE)

    var locked = AtomicBoolean()

    private enum class CMD {
        POLL, PEEK, ADD, EMPTY
    }

    private fun unlock() {
        locked.set(false)
    }

    private fun tryLock() = locked.compareAndSet(false, true)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return combine(Pair(CMD.POLL, null)) { q.poll() }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return combine(Pair(CMD.PEEK, null)) { q.peek() }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        combine(Pair(CMD.ADD, element)) { e: E? ->
            q.add(e)
            e
        }
    }

    private fun combine(p: Pair<CMD, E?>, f: Function<E?, E?>): E? {
        while (true) {
            if (tryLock()) {
                val ret = f.apply(p.second)
                for (i in 0 until FC_ARRAY_SIZE) {
                    val res = fc_array[i].value as Pair<CMD, E?>?
                    if (res != null) {
                        when (res.first) {
                            CMD.ADD -> {
                                q.add(res.second)
                                fc_array[i].compareAndSet(res, Pair(CMD.EMPTY, res.second))
                            }
                            CMD.PEEK -> {
                                fc_array[i].compareAndSet(res, Pair(CMD.EMPTY, q.peek()))
                            }
                            CMD.POLL -> {
                                fc_array[i].compareAndSet(res, Pair(CMD.EMPTY, q.poll()))
                            }
                            else -> {}
                        }
                    }
                }
                unlock()
                return ret
            } else {
                val r = Random(0).nextInt(FC_ARRAY_SIZE)
                if (fc_array[r].compareAndSet(null, p)) {
                    while (locked.get()) {
                        val res = fc_array[r].value as Pair<CMD, E?>? ?: break
                        if ((res.first == CMD.EMPTY) && fc_array[r].compareAndSet(res, null)) {
                            return res.second
                        }
                    }

                    if (fc_array[r].compareAndSet(p, null)) {
                        continue
                    }

                    val res = fc_array[r].value as Pair<CMD, E?>? ?: continue

                    fc_array[r].compareAndSet(res, null)

                    return res?.second
                }
            }
        }
    }
}

const val FC_ARRAY_SIZE = 6