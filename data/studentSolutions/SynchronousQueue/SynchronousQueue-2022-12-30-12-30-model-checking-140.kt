import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */

    private val q = MSQueue<Core<E>>()
    suspend fun send(element: E) {
        while (true) {
            val head = q.head.value
            val tail = q.tail.value
            if ((tail == head) || (tail.x != null && tail.x.isSend.value)) {
                val res = suspendCoroutine sc@ { cont ->
                    if (!q.enqueue(tail, Core(cont, element, true))) {
                        cont.resume(false)
                        return@sc
                    }
                }
                if (res == false) {
                    continue
                } else {
                    return
                }
            } else {
                val top = q.dequeue(head) ?: continue
                top.elem.getAndSet(element)
                top.cont.value!!.resume(true)
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val tail = q.tail.value
            val head = q.head.value
            if ((tail == head) || (tail.x != null && !tail.x.isSend.value)) {
                val core = Core<E>(null, null, false)
                val res = suspendCoroutine sc@ { cont ->
                    core.cont.getAndSet(cont)
                    if (!q.enqueue(tail, core)) {
                        cont.resume(false)
                        return@sc
                    }
                }
                if (res == false) {
                    continue
                } else {
                    return core.elem.value ?: throw RuntimeException()
                }
            } else {
                val top = q.dequeue(head) ?: continue
                top.cont.value!!.resume(true)
                return top.elem.value ?: throw RuntimeException()
            }
        }
    }
}

class Core<E>(cont: Continuation<Any?>?, elem: E?, isSend: Boolean) {
    val cont: AtomicRef<Continuation<Any?>?> = atomic(cont)
    val elem: AtomicRef<E?> = atomic(elem)
    val isSend: AtomicBoolean = atomic(isSend)
}

class MSQueue<E> {
    val head: AtomicRef<Node<E>>
    val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(curTail: Node<E>, x: E): Boolean {
        val newTail = Node(x)
        if (curTail.next.compareAndSet(null, newTail)) {
            tail.compareAndSet(curTail, newTail)
            return true
        } else {
            tail.compareAndSet(curTail, curTail.next.value!!)
            return false
        }
    }

    fun dequeue(curHead: Node<E>): E? {
        val curNextHead = curHead.next.value ?: return null
        if (head.compareAndSet(curHead, curNextHead)) {
            return curNextHead.x
        } else {
            return null
        }
    }

    fun isEmpty(): Boolean = head.value == tail.value
}

class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}