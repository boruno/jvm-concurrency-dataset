import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

private const val NUM = 10

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val list = atomicArrayOfNulls<Pair<Action, E?>>(NUM)

    enum class Action {
        ADD, PEEK, POLL, WAIT, READY
    }

    private fun tryLock() = lock.compareAndSet(false, true)

    private fun unlock() {
        if (!lock.compareAndSet(true, false))
            throw Exception("wtf")
    }

    private fun getValue(exp: Pair<Action, E?>, num: Int) {
        if (!list[num].compareAndSet(exp, null))
            throw Exception("wtf")
    }

    private fun isOpen() = !lock.value


    private fun worker() {
        for (index in (0 until NUM)) {
            val cur = list[index].value ?: continue
            when (cur.first) {
                Action.ADD -> {
                    val set = Pair(Action.WAIT, null)
                    if (list[index].compareAndSet(cur, set)) {
                        q.add(cur.second)
                        list[index].compareAndSet(set, Pair(Action.READY, null))
                    }
                }

                Action.PEEK -> {
                    val set = Pair(Action.WAIT, null)
                    if (list[index].compareAndSet(cur, set)) list[index].compareAndSet(
                        set,
                        Pair(Action.READY, q.peek())
                    )
                }

                Action.POLL -> {
                    val set = Pair(Action.WAIT, null)
                    if (list[index].compareAndSet(cur, set)) list[index].compareAndSet(
                        set,
                        Pair(Action.READY, q.poll())
                    )
                }

                Action.READY -> break

                else -> {
                    assert(false)
                }
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return addToList(Pair(Action.POLL, null))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return addToList(Pair(Action.PEEK, null))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        addToList(Pair(Action.ADD, element))
    }

    private fun addToList(pair: Pair<Action, E?>): E? {
        while (true) {
            if (tryLock()) {
                var ret: E? = null
                if (pair.first == Action.POLL) ret = q.poll()
                if (pair.first == Action.PEEK) ret = q.peek()
                if (pair.first == Action.ADD) q.add(pair.second)
                worker()
                unlock()
                return ret
            }
            val num = Random().nextInt(NUM)
            val req = Pair(pair.first, null)
            if (list[num].compareAndSet(null, req)) {
                while (true) {
                    val cur = list[num].value!!
                    if (cur.first == pair.first) {
                        if (isOpen()) {
                            if (list[num].compareAndSet(cur, null)) break
                        }
                        continue
                    }
                    if (cur.first == Action.READY) {
                        getValue(cur, num)
                        return cur.second
                    }
                }
            }
        }
    }
}
