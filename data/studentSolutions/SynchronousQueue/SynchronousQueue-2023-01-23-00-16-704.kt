import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
open class SynchronousQueue<E> {
    private open class Node {
        val next = AtomicReference<Node>(null)
    }

    private class Receiver<E>(val action: Continuation<E>) : Node()

    private class Sender<E>(val element: E, val action: Continuation<Unit>) : Node()

    private val head : AtomicReference<Node>
    private val tail : AtomicReference<Node>

    init {
        val node = Node()
        head = AtomicReference(node)
        tail = AtomicReference(node)
    }


    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val currentTail = tail.get()
            if (currentTail == head.get() || currentTail is Sender<*>) {
                val result = suspendCoroutine<Any?> sendAttack@{cont ->
                    val newTail = Sender(element, cont)
                    val oldTail = tail.get()
                    if ((oldTail is Sender<*> || oldTail == head.get()) && oldTail.next.compareAndSet(null, newTail)) {
                        tail.compareAndSet(oldTail, newTail)
                    } else {
                        cont.resume(null)
                        return@sendAttack
                    }
                }
                if (result != null) return
            } else {
                val currentHead = head.get()
                if (currentHead == tail.get() || currentHead.next.get() == null) continue
                val nextHead = currentHead.next.get()
                if (nextHead is Receiver<*> && head.compareAndSet(currentHead, nextHead)) {
                    @Suppress("UNCHECKED_CAST")
                    (nextHead.action as Continuation<E>).resume(element)
                    return
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
            val currentTail = tail.get()
            if (currentTail == head.get() || currentTail is Receiver<*>) {
                val result = suspendCoroutine<E?> receiveAttack@{cont ->
                    val newTail = Receiver(cont)
                    val oldTail = tail.get()
                    if ((oldTail is Receiver<*> || oldTail == head.get()) && oldTail.next.compareAndSet(null, newTail)) {
                        tail.compareAndSet(oldTail, newTail)
                    } else {
                        cont.resume(null)
                        return@receiveAttack
                    }
                }
                if (result != null) return result
            } else {
                val currentHead = head.get()
                if (currentHead == tail.get() || currentHead.next.get() == null) continue
                val nextHead = currentHead.next.get()
                if (nextHead is Sender<*> && currentHead != tail.get() && head.compareAndSet(currentHead, nextHead)) {
                    nextHead.action.resume(Unit)
                    @Suppress("UNCHECKED_CAST")
                    return nextHead.element as E
                }
            }
        }
    }
}