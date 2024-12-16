//import kotlinx.atomicfu.atomicArrayOfNulls
//import java.util.*
//import java.util.concurrent.locks.ReentrantLock
//import kotlin.math.abs
//
//class FCPriorityQueue<E : Comparable<E>> {
//    private val q = PriorityQueue<E>()
//    private val n = 15
//    private val lock = ReentrantLock()
//    private val list = atomicArrayOfNulls<Node<E>>(n)
//
//    fun listProcess() {
//        for (i in 0 until n) {
//            if (list[i].value == null) continue
//            val action = list[i].value!!
//            when (action.type) {
//                Action.POLL -> {
//                    val lockedNode: Node<E> = Node(Action.LOCKED, null)
//                    if (list[i].compareAndSet(action, lockedNode)) list[i].compareAndSet(
//                        lockedNode,
//                        Node(Action.REMOVED, q.poll())
//                    )
//                }
//
//                Action.PEAK -> {
//                    val lockedNode: Node<E> = Node(Action.LOCKED, null)
//                    if (list[i].compareAndSet(action, lockedNode)) list[i].compareAndSet(
//                        lockedNode,
//                        Node(Action.REMOVED, q.peek())
//                    )
//                }
//
//                Action.ADD -> {
//                    val lockedNode: Node<E> = Node(Action.LOCKED, null)
//                    if (list[i].compareAndSet(action, lockedNode)) {
//                        q.add(action.element)
//                        list[i].compareAndSet(lockedNode, Node(Action.REMOVED, null))
//                    }
//                }
//
//                else -> {
//                    assert(false)
//                }
//            }
//        }
//    }
//
//    /**
//     * Retrieves the element with the highest priority
//     * and returns it as the result of this function;
//     * returns `null` if the queue is empty.
//     */
//    fun poll(): E? {
//        while (true) {
//            if (lock.tryLock()) {
//                val value = q.poll()
//                listProcess()
//                lock.unlock()
//                return value
//            }
//            val index = abs(Random().nextInt(0, n))
//            if (list[index].compareAndSet(null, Node(Action.POLL, null))) {
//                val node = list[index].value
//                while (true) {
//                    when (list[index].value!!.type) {
//                        Action.POLL -> if (!lock.isLocked && list[index].compareAndSet(list[index].value, null)) break
//                        Action.REMOVED -> if (list[index].compareAndSet(node, null))
//                            return node!!.element
//
//                        else -> {}
//                    }
//                }
//            }
//        }
//    }
//
//    /**
//     * Returns the element with the highest priority
//     * or `null` if the queue is empty.
//     */
//    fun peek(): E? {
//        while (true) {
//            if (lock.tryLock()) {
//                val value = q.peek()
//                listProcess()
//                lock.unlock()
//                return value
//            }
//            val index = abs(Random().nextInt(0, n))
//            if (list[index].compareAndSet(null, Node(Action.PEAK, null))) {
//                val node = list[index].value
//                while (true) {
//                    when (list[index].value!!.type) {
//                        Action.PEAK -> if (!lock.isLocked && list[index].compareAndSet(list[index].value, null)) break
//                        Action.REMOVED -> if (list[index].compareAndSet(node, null))
//                            return node!!.element
//
//                        else -> {}
//                    }
//                }
//            }
//        }
//    }
//
//    /**
//     * Adds the specified element to the queue.
//     */
//    fun add(element: E) {
//        while (true) {
//            if (lock.tryLock()) {
//                q.add(element)
//                listProcess()
//                lock.unlock()
//                return
//            }
//            val index = abs(Random().nextInt(0, n))
//            if (list[index].compareAndSet(null, Node(Action.ADD, null))) {
//                while (true) {
//                    when (list[index].value!!.type) {
//                        Action.ADD -> if (!lock.isLocked && list[index].compareAndSet(list[index].value, null)) break
//                        Action.REMOVED -> if (list[index].compareAndSet(list[index].value, null))
//                            return
//
//                        else -> {}
//                    }
//                }
//            }
//        }
//    }
//
//    enum class Action {
//        POLL, PEAK, ADD, LOCKED, REMOVED
//    }
//
//    data class Node<E>(val type: Action, val element: E?)
//}
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

private const val NUM = 10

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()
    private val list = atomicArrayOfNulls<Pair<Action, E?>>(NUM)

    enum class Action {
        ADD, PEEK, POLL, WAIT, READY
    }

    fun worker() {
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

                Action.READY -> continue

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
        while (true) {
            if (lock.tryLock()) {
                val ret = q.poll()
                worker()
                lock.unlock()
                return ret
            }
            val num = Random().nextInt(NUM)
            val req = Pair(Action.POLL, null)
            if (list[num].compareAndSet(null, req)) {
                while (true) {
                    val cur = list[num].value!!
                    if (cur.first == Action.POLL) {
                        if (!lock.isLocked) {
                            if (list[num].compareAndSet(cur, null)) break
                        }
                        continue
                    }
                    if (cur.first == Action.READY) {
                        list[num].compareAndSet(cur, null)
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
        while (true) {
            if (lock.tryLock()) {
                val ret = q.peek()
                worker()
                lock.unlock()
                return ret
            }
            val num = Random().nextInt(NUM)
            val req = Pair(Action.PEEK, null)
            if (list[num].compareAndSet(null, req)) {
                while (true) {
                    val cur = list[num].value!!
                    if (cur.first == Action.PEEK) {
                        if (!lock.isLocked) {
                            if (list[num].compareAndSet(cur, null)) break
                        }
                        continue
                    }
                    if (cur.first == Action.READY) {
                        list[num].compareAndSet(cur, null)
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
        while (true) {
            if (lock.tryLock()) {
                q.add(element)
                worker()
                lock.unlock()
                return
            }
            val num = Random().nextInt(NUM)
            val req = Pair(Action.ADD, element)
            if (list[num].compareAndSet(null, req)) {
                while (true) {
                    val cur = list[num].value!!
                    if (cur.first == Action.ADD) {
                        if (!lock.isLocked) {
                            if (list[num].compareAndSet(cur, null)) break
                        }
                        continue
                    }
                    if (cur.first == Action.READY) {
                        list[num].compareAndSet(cur, null)
                        return
                    }
                }
            }
        }
    }
}
