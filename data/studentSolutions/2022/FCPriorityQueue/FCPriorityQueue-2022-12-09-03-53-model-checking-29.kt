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
    enum class Type {
        POLL, PEEK, ADD, LOCKED, REMOVED
    }

//    class Node<E>(val type: Type, val element: E?) {}

    private val q = PriorityQueue<E>()
    private val n = 15
    private val lock = ReentrantLock()
    private val nodes = atomicArrayOfNulls<Pair<Type, E?>>(n)

    fun worker() {
        for (index in (0 until NUM)) {
            val cur = nodes[index].value ?: continue
            when (cur.first) {
                Type.ADD -> {
                    val set = Pair(Type.LOCKED, null)
                    if (nodes[index].compareAndSet(cur, set)) {
                        q.add(cur.second)
                        nodes[index].compareAndSet(set, Pair(Type.REMOVED, null))
                    }
                }

                Type.PEEK -> {
                    val set = Pair(Type.LOCKED, null)
                    if (nodes[index].compareAndSet(cur, set)) nodes[index].compareAndSet(
                        set,
                        Pair(Type.REMOVED, q.peek())
                    )
                }

                Type.POLL -> {
                    val set = Pair(Type.REMOVED, null)
                    if (nodes[index].compareAndSet(cur, set)) nodes[index].compareAndSet(
                        set,
                        Pair(Type.REMOVED, q.poll())
                    )
                }

                Type.REMOVED -> continue

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
            val req = Pair(Type.POLL, null)
            if (nodes[num].compareAndSet(null, req)) {
                while (true) {
                    val cur = nodes[num].value!!
                    if (cur.first == Type.POLL) {
                        if (!lock.isLocked) {
                            if (nodes[num].compareAndSet(cur, null)) break
                        }
                        continue
                    }
                    if (cur.first == Type.REMOVED) {
                        nodes[num].compareAndSet(cur, null)
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
            val req = Pair(Type.PEEK, null)
            if (nodes[num].compareAndSet(null, req)) {
                while (true) {
                    val cur = nodes[num].value!!
                    if (cur.first == Type.PEEK) {
                        if (!lock.isLocked) {
                            if (nodes[num].compareAndSet(cur, null)) break
                        }
                        continue
                    }
                    if (cur.first == Type.REMOVED) {
                        nodes[num].compareAndSet(cur, null)
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
            }
            val num = Random().nextInt(NUM)
            val req = Pair(Type.ADD, null)
            if (nodes[num].compareAndSet(null, req)) {
                while (true) {
                    val cur = nodes[num].value!!
                    if (cur.first == Type.ADD) {
                        if (!lock.isLocked) {
                            if (nodes[num].compareAndSet(cur, null)) break
                        }
                        continue
                    }
                    if (cur.first == Type.REMOVED) {
                        nodes[num].compareAndSet(cur, null)
                        return
                    }
                }
            }
        }
    }
}
