import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val RETRY: Int = 100

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {

    private inner class Node(
        type: Type? = null,
        element: E? = null
    ) {
        val next: AtomicReference<Node?> = AtomicReference(null)
        val type: AtomicReference<Type?> = AtomicReference(type)
        val element: AtomicReference<E?> = AtomicReference(element)
        val continuation: AtomicReference<Continuation<Any?>?> = AtomicReference(null)

        fun isSender() = type.get() == Type.SENDER
        fun isReceiver() = type.get() == Type.RECEIVER
    }
    private suspend fun enqueueAndSuspend(tail: Node, node: Node): Boolean {
        val res = suspendCoroutine<Any?> sc@{ cont ->
            node.continuation.set(cont)
            if (!tail.next.compareAndSet(null, node)) {
                this.tail.compareAndSet(tail, tail.next.get())
                cont.resume(RETRY)
                return@sc
            } else {
                this.tail.compareAndSet(tail, node)
            }
        }
        return res != RETRY
    }

    private fun dequeueAndResume(head: Node, element: E?): Boolean {
        val next = head.next.get() ?: return false

        return if (this.head.compareAndSet(head, next)) {
            if (element != null) {
                next.element.set(element)
            }
            next.continuation.get()?.resume(null) ?: throw NullPointerException()
            true
        } else {
            false
        }
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val node = Node(Type.SENDER, element)
            val curTail = tail.get()
            val curHead = head.get()

            if (curTail == tail.get()) {
                if (curTail == curHead || curTail.isSender()) {
                    if (enqueueAndSuspend(curTail, node)) return
                } else {
                    if (curTail == tail.get()) {
                        if (dequeueAndResume(curTail, element)) return
                    }
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val node = Node(Type.RECEIVER)
            val curTail = tail.get()
            val curHead = head.get()

            if (curTail == tail.get()) {
                if (curTail == curHead || curTail.isReceiver()) {
                    if (enqueueAndSuspend(curTail, node)) {
                        return node.element.get() ?: throw IllegalArgumentException()
                    }
                } else {
                    if (curTail == tail.get()) {
                        if (dequeueAndResume(curHead, null)) {
                            return curHead.next.get()?.element?.get() ?: throw NullPointerException()
                        }
                    }
                }
            }
        }
    }


    private val dummy = Node()
    private var head: AtomicReference<Node> = AtomicReference(dummy)
    private var tail: AtomicReference<Node> = AtomicReference(dummy)
}

//private class Node<E>(type: Type? = null, value: E? = null) {
//    val next: AtomicReference<Node<E>?> = AtomicReference(null)
//    val type: AtomicReference<Type?> = AtomicReference(type)
//    val value: AtomicReference<E?> = AtomicReference(value)
//    val continuation: AtomicReference<Continuation<Any?>?> = AtomicReference(null)
//    fun isSender() = type.get() == Type.SENDER
//    fun isReceiver() = type.get() == Type.RECEIVER
//}

private enum class Type { SENDER, RECEIVER }


