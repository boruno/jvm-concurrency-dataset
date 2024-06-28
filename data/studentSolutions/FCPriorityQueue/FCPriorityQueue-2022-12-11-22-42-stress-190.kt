import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.*

enum class OpType {
    POLL, PEEK, ADD, CREATE
}

data class Publication<E>(val opType: OpType, val x: E?, var completed: Boolean = false)

class Node<E>(val publication: Publication<E>) {
    val next = atomic<Node<E>?>(null)
}

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>
    private val lock = ReentrantLock()

    init {
        val dummy = Node<E>(Publication(OpType.CREATE, null))
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    fun poll(): E? {
        val publication = Publication<E>(OpType.POLL, null)
        val node = enqueue(publication)
        if (lock.tryLock()) {
            traversePublications(node)
            lock.unlock()
        } else {
            while (!node.publication.completed) {}
        }
        return q.poll()
    }

    fun peek(): E? {
        val publication = Publication<E>(OpType.PEEK, null)
        val node = enqueue(publication)
        if (lock.tryLock()) {
            traversePublications(node)
            lock.unlock()
        } else {
            while (!node.publication.completed) {}
        }
        return q.peek()
    }

    fun add(element: E) {
        val publication = Publication(OpType.ADD, element)
        val node = enqueue(publication)
        if (lock.tryLock()) {
            traversePublications(node)
            lock.unlock()
        }
    }

    private fun traversePublications(stopNode: Node<E>) {
        while (true) {
            val node = dequeue() ?: break
            if (node.publication.opType == OpType.ADD)
                q.add(node.publication.x!!)
            node.publication.completed = true
            if (node === stopNode)
                break
        }
    }

    private fun dequeue(): Node<E>? {
        val curHead = head.value
        val curHeadNext = curHead.next.value ?: return null
        head.value = curHeadNext
        return curHeadNext
    }

    private fun isEmpty(): Boolean {
        val curHead = head.value
        return curHead.next.value == null
    }

    private fun enqueue(publication: Publication<E>): Node<E> {
        val newTail = Node(publication)
        while (true) {
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
                break
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
            }
        }
        return newTail
    }
}