import kotlin.coroutines.resume
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import java.util.concurrent.atomic.AtomicReference

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicReference<Node>
    private val tail: AtomicReference<Node>

    init {
        val initialNode = Node()
        head = AtomicReference(initialNode)
        tail = AtomicReference(initialNode)
    }


    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val tail = this.tail.get()
            if (tail == head.get() || tail is Sender<*>) {
                val res = suspendCoroutine<Any> sc@{ cont ->
                    val newTail = Sender(element, cont)
                    val oldTail = this.tail.get()
                    if ((oldTail == head.get() || oldTail is Sender<*>) &&
                        oldTail.next.compareAndSet(null, newTail)) {
                        this.tail.compareAndSet(oldTail, newTail)
                    } else {
//                        cont.resume("retry")
                        return@sc
                    }
                }
                if (res != "retry") {
                    return
                }
                continue
            }
            val head = this.head.get()
            if (head == this.tail.get() || head.next.get() == null) {
                continue
            } else {
                val headNext = head.next.get()
                if (headNext is Receiver<*> && this.head.compareAndSet(head, headNext)) {
//                    (headNext.f as Continuation<E>).resume(element)
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
            val tail = this.tail.get()
            if (tail == this.head.get() || tail is Receiver<*>) {
                val res = suspendCoroutine<E?> sc@{ cont ->
                    val newTail = Receiver(cont)
                    val oldTail = this.tail.get()
                    if ((oldTail == head.get() || oldTail is Receiver<*>) &&
                        oldTail.next.compareAndSet(null, newTail)) {
                        this.tail.compareAndSet(oldTail, newTail)
                    } else {
//                        cont.resume(null)
                        return@sc
                    }
                }
                if (res != null) {
                    return res
                }
                continue
            }
            val head = this.head.get()
            if (head == this.tail.get() || head.next.get() == null) {
                continue
            }
            val headNext = head.next.get()
            if (head != this.tail.get() && headNext is Sender<*> &&
                this.head.compareAndSet(head, headNext)) {
//                headNext.f.resume(Unit)
                return (headNext.value as E)
            }
        }
    }


    private open class Node {
        val next = AtomicReference<Node>(null)
    }

    private class Receiver<E>(
        val f: Continuation<E>
    ) : Node()

    private class Sender<E>(
        val value: E,
        val f: Continuation<Unit>
    ) : Node()
}
