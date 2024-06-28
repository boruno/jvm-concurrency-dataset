import kotlinx.atomicfu.atomic
import java.util.*

@Suppress("UNCHECKED_CAST")
class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val id = ThreadLocal.withInitial { getNewID() }
    private val arr = arrayOfNulls<Any>(THREAD_COUNT)
    private val lock = atomic(false)

    private fun getNewID(): Int {
        var x = curThreadID.getAndIncrement()
        if (x >= THREAD_COUNT) x -= THREAD_COUNT
        return x
    }

    private fun abstractLockOperation(operation: () -> E?): E? {
        val curID = id.get()
        arr[curID] = operation

        while (arr[curID] == operation) {
            if (tryLock()) {
                for (i in arr.indices) {
                    val element = arr[i]
                    if (element is Function0<*>) arr[i] = element()
                }

                unlock()
            }
        }

        return arr[curID] as E?
    }

    private fun tryLock() = if (!lock.value) lock.compareAndSet(false, update = true) else false

    private fun unlock() {
        lock.value = false
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return abstractLockOperation { q.poll() }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return abstractLockOperation { q.peek() }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        abstractLockOperation {
            q.add(element)
            null
        }
    }
}

private const val THREAD_COUNT = 5
private val curThreadID = atomic(0)

/*import kotlinx.atomicfu.atomic
import java.util.*

@Suppress("UNCHECKED_CAST")
class FCPriorityQueue<E : Comparable<E>> {
    private val queue = PriorityQueue<E>()
    private val index = ThreadLocal.withInitial { threadCnt.getAndIncrement() % THREAD_NUMBER }
    private val arr = arrayOfNulls<Any>(THREAD_NUMBER)
    private val lock = atomic(false)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return abstractLockOperation { queue.poll() }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return abstractLockOperation { queue.peek() }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        abstractLockOperation {
            queue.add(element)
            null
        }
    }

    private fun abstractLockOperation(operation: () -> E?): E? {
        arr[index.get()] = operation

        while (arr[index.get()] === operation) {
            if (tryLock()) {
                for (i in arr.indices) {
                    val element = arr[i]

                    if (element is Function0<*>) arr[i] = element()
                }

                unlock()
            }
        }

        return arr[index.get()] as E?
    }

    private fun tryLock() = if (!lock.value) lock.compareAndSet(false, update = true) else false

    private fun unlock() { lock.value = false }
}

private val threadCnt = atomic(0)
private const val THREAD_NUMBER = 3
*/


/*
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.lang.Integer.min
import java.util.*

class FCPriorityQueue<E: Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val operationsArray = atomicArrayOfNulls<Node<E>>(SIZE)
    private val random = Random()
    private val locked: AtomicBoolean = atomic(false)

    fun add(element: E) {
        if (doAdd(element))
            return

        val node = Node(Operation.ADD, element)
        var index: Int
        var flag = true

        do {
            index = random.nextInt(SIZE)
            for (i in index until min(index + DELTA, SIZE)) {
                val newFlag = operationsArray[i].compareAndSet(null, node)
                if (!newFlag && doAdd(element)) return
                else if (newFlag) {
                    flag = false
                    index = i
                    break
                }
            }
        } while (flag)

        while (true) {
            if (!locked.value) {
                if (locked.compareAndSet(expect = false, update = true)) {
                    val result = operationsArray[index]
                    if (result.value!!.op == Operation.ADD)
                        q.add(element)
                    operationsArray[index].compareAndSet(result.value, null)
                    doAllOperations()
                    locked.compareAndSet(expect = true, update = false)
                    break
                }
            }
            if (operationsArray[index].compareAndSet(Node(Operation.FINISH, null), null))
                break
        }
    }

    fun poll(): E? {
        var ans = doPoll()
        if (ans.hasResult == 1)
            return ans.element

        val node = Node<E>(Operation.POLL, null)
        var index: Int
        var flag = true

        do {
            index = random.nextInt(SIZE)
            for (i in index until min(index + DELTA, SIZE)) {
                val newFlag = operationsArray[i].compareAndSet(null, node)
                if (!newFlag) {
                    ans = doPoll()
                    if (ans.hasResult == 1) return ans.element
                }
                else if (newFlag) {
                    flag = false
                    index = i
                    break
                }
            }
        } while (flag)

        while (true) {
            if (!locked.value) {
                if (locked.compareAndSet(expect = false, update = true)) {
                    val result = operationsArray[index]
                    var answer: E? = result.value!!.element
                    if (result.value!!.op != Operation.FINISH)
                        answer = q.poll()
                    operationsArray[index].compareAndSet(result.value, null)
                    doAllOperations()
                    locked.compareAndSet(expect = true, update = false)
                    return answer
                }
            }
            val answer = operationsArray[index].value!!.element
            if (operationsArray[index].compareAndSet(Node(Operation.FINISH, null), null))
                return answer
        }
    }

    fun peek(): E? {
        return q.peek()
    }

    private fun doAdd(element: E): Boolean {
        if (locked.value)
            return false
        if (locked.compareAndSet(expect = false, update = true)) {
            q.add(element)
            doAllOperations()
            locked.compareAndSet(expect = true, update = false)
            return true
        }
        return false
    }

    private fun doPoll(): PollResult<E?> {
        if (locked.value)
            return PollResult(0)
        if (locked.compareAndSet(expect = false, update = true)) {
            val res = q.poll()
            doAllOperations()
            locked.compareAndSet(expect = true, update = false)
            return PollResult(1, res)
        }
        return PollResult(0)
    }

    private fun doAllOperations() {
        for (i in 0 until SIZE) {
            val curOp = operationsArray[i].value
            if (curOp != null) {
                if (curOp.op == Operation.ADD) {
                    q.add(curOp.element)
                    operationsArray[i].value = Node(Operation.FINISH, null)
                }
                else if (curOp.op == Operation.POLL)
                    operationsArray[i].value = Node(Operation.FINISH, q.poll())
            }
        }
    }

    private class Node<E>(o: Operation, el: E?) {
        val op: Operation = o
        val element: E? = el
    }

    private class PollResult<E>(p: Int, el: E? = null) {
        val hasResult: Int = p
        val element: E? = el
    }

    enum class Operation {
        ADD,
        POLL,
        FINISH
    }
}

const val SIZE = 20
const val DELTA = 5

*/