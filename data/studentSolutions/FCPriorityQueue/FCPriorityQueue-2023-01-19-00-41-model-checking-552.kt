import kotlinx.atomicfu.*
import java.util.*

enum class OperName {
    POLL, PEEK, ADD, DONE
}
data class Oper<E>(val name: OperName, val element: E?);

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val locked = atomic(false)
    private val operSize = 2 * Thread.activeCount();
    private val operations = atomicArrayOfNulls<Oper<E>?>(operSize)

    fun tryLock() = locked.compareAndSet(expect = false, update = true)

    fun unlock() {
        locked.compareAndSet(true, false)
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return addOperation(Oper(OperName.POLL, null));
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return addOperation(Oper(OperName.PEEK, null));
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        addOperation(Oper(OperName.ADD, element));
    }

    private fun addOperation(oper: Oper<E>): E? {
        while (true) {
            if (tryLock()) {
                var ans: E? = null;
                if (oper.name == OperName.PEEK) {
                    ans = q.peek();
                }
                if (oper.name == OperName.POLL) {
                    ans = q.poll();
                }
                if (oper.name == OperName.ADD) {
                    q.add(oper.element);
                }
                for (i in 0 until operSize) {
                    val o = operations[i].value;
                    if (o == null) {
                        continue;
                    }
                    if (o.name == OperName.ADD) {
                        if (operations[i].compareAndSet(o, Oper(OperName.DONE, null))) {
                            q.add(o.element)
                        }
                    } else if (o.name == OperName.POLL) {
                        val ans = q.poll();
                        if (operations[i].compareAndSet(o, Oper(OperName.DONE, ans))) {
                            if (ans != null) {
                                q.add(ans)
                            }
                        }
                    } else if (o.name == OperName.PEEK) {
                        val ans = q.peek();
                        operations[i].compareAndSet(o, Oper(OperName.DONE, ans))
                    }
                }
                unlock();
                return ans;
            } else {
                var ans: Pair<Boolean, E?> = Pair(false, null);
                for (i in 0 until operSize) {
                    if (operations[i].compareAndSet(null, oper)) {
                        for (t in 0 until 10) {
                            val o = operations[i].value!!;
                            if (o.name == OperName.DONE && operations[i].compareAndSet(o, null)) {
                                ans = Pair(true, o.element);
                            }
                        }
                        if (!operations[i].compareAndSet(oper, null)) {
                            val o = operations[i].value!!;
                            operations[i].compareAndSet(o, null);
                            ans = Pair(true, o.element);
                        }
                    }
                }
                if (ans.first) {
                    return ans.second;
                }
            }
        }

    }


}