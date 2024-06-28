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
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null, null, false)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private fun enqueueAndSuspend(node: Node<E>, isSender: Boolean, curTail: Node<E>) {
//        while (true) {
        for (aaa in 1..100) {
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                return
            } else {
                curTail.next.value?.let { tail.compareAndSet(curTail, it) }
            }
        }
        throw Exception("ENQUEUE AND SUSPEND EXCEPTION")
    }

    private fun dequeue(curHead: Node<E>): Any {
//        while (true) {
        for (aaa in 1..100) {
            val curHeadNext = curHead.next.value ?: continue
            if (head.compareAndSet(curHead, curHeadNext)) {
                return (curHeadNext.cont to curHeadNext.element)
            }
        }
        throw Exception("DEQUE EXCEPTION")
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun send(element: E): Unit {
        val t = tail.value
        val h = head.value

        if (h == t || t.isSender) {
            suspendCoroutine<Unit> { cont ->
                enqueueAndSuspend(Node(cont, element, true), true, t)
            }
        } else {
            val (cont, _) = dequeue(h) as Pair<Continuation<E>, Any?>
            cont.resume(element)
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun receive(): E {
        val t = tail.value
        val h = head.value

        return if (h == t || !t.isSender) {
            suspendCoroutine { cont ->
                enqueueAndSuspend(Node(cont, null, false), false, t)
            }
        } else {
            val (cont, elem) = dequeue(h) as Pair<Continuation<Unit>, E>
            cont.resume(Unit)
            elem
        }
    }
}

private class Node<E>(val cont: Any?, val element: E?, val isSender: Boolean) {
    val next = atomic<Node<E>?>(null)
}