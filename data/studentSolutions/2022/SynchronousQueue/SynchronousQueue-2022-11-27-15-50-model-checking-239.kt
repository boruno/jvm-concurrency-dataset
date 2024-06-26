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
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            val curHeadNext = curHead.next.value

            if (curHead == curTail || curTail.cell is Sender<*>) {
                val res = suspendCoroutine { cont ->
                    val newNode = Node(Sender(cont, element))

                    if (curTail.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(curTail, newNode)
                    } else {
                        tail.compareAndSet(curTail, curTail.next.value!!)
                        cont.resume(RETRY)
                    }
                }

                if (res !== RETRY) {
                    return
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                val receiver = curHeadNext!!.cell as Receiver<E>

                if (head.compareAndSet(curHead, curHeadNext)) {
                    receiver.cont.resume(element)
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
            val curHead = head.value
            val curTail = tail.value
            val curHeadNext = curHead.next.value

            if (curHead == curTail || curHeadNext!!.cell is Receiver<*>) {
                val res = suspendCoroutine { cont ->
                    val newNode = Node(Receiver(cont))

                    if (curTail.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(curTail, newNode)
                    } else {
                        tail.compareAndSet(curTail, curTail.next.value!!)
                        cont.resume(RETRY)
                    }
                }

                if (res !== RETRY) {
                    println("!!!")
                    @Suppress("UNCHECKED_CAST")
                    return res as E
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                val sender = curHeadNext!!.cell as Sender<E>

                if (head.compareAndSet(curHead, curHeadNext)) {
                    sender.cont.resume(Unit)
                    return sender.element
                }
            }
        }
    }
}

private sealed interface Cell
private class Sender<E>(val cont: Continuation<Unit>, val element: E): Cell
private class Receiver<E>(val cont: Continuation<E>): Cell

private class Node(val cell: Cell?) {
    val next: AtomicRef<Node?> = atomic(null)
}

private object RETRY