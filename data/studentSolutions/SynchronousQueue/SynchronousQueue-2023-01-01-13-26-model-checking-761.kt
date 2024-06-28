import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val WAIT: Int = 100

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {

    private val dummy = Node<E>()
    private var head: AtomicReference<Node<E>> = AtomicReference(dummy)
    private var tail: AtomicReference<Node<E>> = AtomicReference(dummy)

    private suspend fun enqueueAndSuspend(curTail: Node<E>, node: Node<E>): Boolean {
        val result = suspendCoroutine suspendCoroutine@{ continuation ->
            node.continuation.set(continuation)
            if (!curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, curTail.next.get())
                continuation.resume(WAIT)
                return@suspendCoroutine
            } else {
                tail.compareAndSet(curTail, node)
            }
        }
        return result != WAIT
    }

    private fun dequeueAndResume(curHead: Node<E>, element: E?): Boolean {
        val next = curHead.next.get() ?: return false

        return if (head.compareAndSet(curHead, next)) {
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
    suspend fun send(element: E) {
        while (true) {
            val curHead = head.get()
            val curTail = tail.get()

            val node = Node(Type.SENDER, element)

//            if (curTail == tail.get()) {
//                if (curTail == curHead || curTail.isSender()) {
//                    if (enqueueAndSuspend(curTail, node)) return
//                } else {
//                    if (curTail == tail.get()) {
//                        if (dequeueAndResume(curHead, element)) return
//                    }
//                }
//            }
            if (curTail == tail.get()) {
                if (dequeueAndResume(curHead, element)) return
                if (curTail == curHead || curTail.isSender()) {
                    if (enqueueAndSuspend(curTail, node)) return
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
            val curHead = head.get()
            val curTail = this.tail.get()

            val node = Node<E>(Type.RECEIVER)

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
}


private enum class Type { SENDER, RECEIVER }

private class Node<E>(type: Type? = null, element: E? = null) {
    val next: AtomicReference<Node<E>?> = AtomicReference(null)
    val type: AtomicReference<Type?> = AtomicReference(type)
    val element: AtomicReference<E?> = AtomicReference(element)
    val continuation: AtomicReference<Continuation<Any?>?> = AtomicReference(null)

    fun isSender() = type.get() == Type.SENDER
    fun isReceiver() = type.get() == Type.RECEIVER
}



