import kotlin.coroutines.*
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.loop

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node(MSSyncElement())
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val curTail = tail.value
            val curHead = head.value
            if (curHead.next.value == null || curTail.x.category == TaskCategory.Send) {
                // transfer coroutine to the rendevouz moment
                val res = suspendCoroutine<Any?> sc@{ cont ->
                    val wrap = MSSyncElement(cont, TaskCategory.Send, element)
                    if (!enqueue(wrap, curTail)) {
                        cont.resume(false) // try transfer again if smth went wrong
                        return@sc
                    }
                }
                if (res == true) return
            } else {
                val res = dequeue(curHead)
                if (res == null) {
                    continue
                }
//                res.element.getAndSet(element)
                res.continuation.value!!.resume(true to element)
                return
            }
        }
    }
    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val curTail = tail.value
            val curHead = head.value
            if (curHead.next.value == null || curTail.x.category == TaskCategory.Receive) {
                // transfer coroutine to the rendevouz moment
                val (res, value) = suspendCoroutine<Pair<Boolean, Any?>> sc@{ cont ->
                    val wrap = MSSyncElement(cont, TaskCategory.Receive)
                    if (!enqueue(wrap, curTail)) {
                        cont.resume(false to null) // try transfer again if smth went wrong
                        return@sc
                    }
                }
                if (res) return value as E
            } else {
                val res = dequeue(curHead)
                if (res == null) {
                    continue
                }
                res.continuation.value!!.resume(true to null)
                return res.element as E
            }
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: MSSyncElement, it: Node): Boolean {
        val newNode = Node(x)
//        val it = tail.value
        if (tail.value.next.compareAndSet(null, newNode)) {
            tail.compareAndSet(it, newNode)
            return true
        } else {
            tail.compareAndSet(it, it.next.value!!)
            return false
        }

    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(it: Node): MSSyncElement? {
//        val it = head.value
        if (it.next.value == null) {
            return null
        }
        if (head.compareAndSet(it, it.next.value!!)) {
            return it.next.value!!.x
        }
        return null
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null
    }

}

class MSSyncElement(
    continuation : Continuation<Pair<Boolean, Any?>>? = null,
    val category: TaskCategory = TaskCategory.Empty,
    val element: Any? = null
) {
    val continuation: AtomicRef<Continuation<Pair<Boolean, Any?>>?> = atomic(continuation)
//    val element: AtomicRef<Any?> = atomic(element)

}

class Node(val x: MSSyncElement) {
    val next = atomic<Node?>(null)
}
enum class TaskCategory {
    Empty, Receive, Send
}