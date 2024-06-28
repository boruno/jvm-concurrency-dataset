import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import mpp.msqueue.MSQueue
import mpp.msqueue.Receiver
import mpp.msqueue.Sender
import mpp.msqueue.Operation
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */

class SynchronousQueue<E> {

    private val queue = MSQueue<E>()
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val head = queue.head.value
            val tail = queue.tail.value

            if (head == tail || tail is Sender<*>) {
                suspendCoroutine<Any?> {
                    if (queue.tail.value == tail) {
                        queue.enqueue(Sender<E>(element, it))
                    } else {
                        it.resume(element)
                    }
                }
            } else {
                val receiver = queue.dequeue()
                (receiver as Receiver<*>).continuation?.resume(element)
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while(true) {
            val head = queue.head.value
            val tail = queue.tail.value

             if (head == tail || tail is Receiver<*>) {
                 suspendCoroutine<Any?> {
                    if (queue.tail.value == tail) {
                        queue.enqueue(Receiver<E>(null, it))
                    } else {
                        it.resume(null)
                    }
                }
             } else {
                val sender = queue.dequeue()
                (sender as Sender<*>).continuation?.resume(null)
            }
        }
    }
}

