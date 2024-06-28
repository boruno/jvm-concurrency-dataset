import Type.Receiver
import Type.Sender
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class Type {
    Sender,
    Receiver,
}

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val queue = MSQueue<Pair<Pair<Continuation<Any?>, E?>, Type>>()

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val tail = queue.tail.value
            val head = queue.head.value
            if (head.next.value == null || tail.x?.second == Sender) {
                if (suspendCoroutine<Any?> { cont ->
                    val cur = Node((cont to (element as E?)) to Sender)
                    if (tail.next.compareAndSet(null, cur)) {
                        queue.tail.compareAndSet(tail, cur)
                    } else {
                        queue.tail.compareAndSet(tail, tail.next.value!!)
                        cont.resume(null)
                    }
                } != null) {
                   return
                }
            } else {
                val next = head.next.value ?: continue
                if (queue.head.compareAndSet(head, next)) {
                    next.x!!.first.first.resume(element)
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
            val tail = queue.tail.value
            val head = queue.head.value
            if (head.next.value == null || tail.x!!.second == Receiver) {
                return suspendCoroutine<Any?> { cont ->
                        val cur = Node((cont to (null as E?)) to Receiver)
                        if (tail.next.compareAndSet(null, cur)) {
                            queue.tail.compareAndSet(tail, cur)
                        } else {
                            queue.tail.compareAndSet(tail, tail.next.value!!)
                            cont.resume(null)
                        }
                    } as E? ?: continue
            } else {
                val next = head.next.value ?: continue
                if (queue.head.compareAndSet(head, next)) {
                    next.x!!.first.first.resume(Any())
                    return next.x.first.second!!
                }
            }
        }
    }
}