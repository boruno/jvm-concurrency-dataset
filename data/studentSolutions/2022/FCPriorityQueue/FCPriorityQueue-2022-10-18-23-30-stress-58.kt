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


    fun worker() {
        loop@ for (index in (0 until NUM)) {
//            while (true) {
            val cur = list[index].value ?: continue@loop
            when (cur.first) {
                Action.ADD -> {
                    val set = Pair(Action.WAIT, null)
                    if (list[index].compareAndSet(cur, set)) {
                        val ret = Pair(Action.READY, null)
                        q.add(cur.second)
                        if (!list[index].compareAndSet(cur, ret))
                            throw Exception("wtf")
                    }
                }
                Action.PEEK -> {
                    val ret = Pair(Action.READY, q.peek())
                    list[index].compareAndSet(cur, ret)
                }
                Action.POLL -> {
                    val set = Pair(Action.WAIT, null)
                    if (list[index].compareAndSet(cur, set)) {
                        val ret = Pair(Action.READY, q.poll())
                        if (!list[index].compareAndSet(set, ret))
                            throw Exception("wtf")
                    }
                }
                Action.WAIT -> throw Exception("wtf")
                Action.READY -> continue@loop
            }
//            print(q)
//            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        loop@ while (true) {
            if (tryLock()) {
                val ret = q.poll()
                worker()
                unlock()
                return ret
            }
            val num = Random().nextInt(NUM)
            val req = Pair(Action.POLL, null)
            if (list[num].compareAndSet(null, req)) {
                while (true) {
                    val cur = list[num].value
                    if (cur == null || cur.first == Action.PEEK || cur.first == Action.ADD)
                        throw Exception("wtf")
                    if (cur.first == Action.POLL) {
                        if (isOpen()) {
                            if (list[num].compareAndSet(cur, null))
                                continue@loop
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

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        loop@ while (true) {
            if (tryLock()) {
                val ret = q.peek()
                worker()
                unlock()
                return ret
            }
            val num = Random().nextInt(NUM)
            val req = Pair(Action.PEEK, null)
            if (list[num].compareAndSet(null, req)) {
                while (true) {
                    val cur = list[num].value
                    if (cur == null || cur.first == Action.POLL || cur.first == Action.ADD)
                        throw Exception("wtf")
                    if (cur.first == Action.PEEK) {
                        if (isOpen()) {
                            if (list[num].compareAndSet(cur, null))
                                continue@loop
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

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        loop@ while (true) {
            if (tryLock()) {
                q.add(element)
                worker()
                unlock()
                return
            }
            val num = Random().nextInt(NUM)
            val req = Pair(Action.ADD, element)
            if (list[num].compareAndSet(null, req)) {
                while (true) {
                    val cur = list[num].value
                    if (cur == null || cur.first == Action.POLL || cur.first == Action.PEEK)
                        throw Exception("wtf")
                    if (cur.first == Action.ADD) {
                        if (isOpen()) {
                            if (list[num].compareAndSet(cur, null))
                                continue@loop
                        }
                        continue
                    }
                    if (cur.first == Action.READY) {
                        getValue(cur, num)
                        return
                    }
                }
            }
        }
    }
}