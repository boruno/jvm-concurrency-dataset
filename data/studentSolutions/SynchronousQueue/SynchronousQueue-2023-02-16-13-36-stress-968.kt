@file:Suppress("UNCHECKED_CAST")

import kotlinx.atomicfu.atomic
import mpp.msqueue.MSQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */

open class Operation<E>(
    open val element: E?,
    open val continuation: Continuation<Any?>?
) {
    val next = atomic<Operation<E>?>(null)
}

data class Sender<E>(
    override val element: E,
    override val continuation: Continuation<Any?>?
) : Operation<E>(element, continuation)

data class Receiver<E>(
    override val element: E?,
    override val continuation: Continuation<Any?>?
) : Operation<E>(element, continuation)

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
                val result = suspendCoroutine<Any?> {
                    if (queue.tail.value == tail) {
                        val operation = Sender<E>(element, it)
//                        queue.enqueue(operation)

//                        val curTail = queue.tail.value
                        if (tail.next.compareAndSet(null, operation)) {
                            queue.tail.compareAndSet(tail, operation)
                        } else {
                            val next = tail.next.value
                            if (next != null) {
                                queue.tail.compareAndSet(tail, next)
                            }
                            it.resume(element)
//                            if (tail.next.value is Receiver<*>) {
//                                val receiver = queue.dequeue()
//                                receiver?.continuation?.resume(element)
//                            } else {
//                                val next = curTail.next.value
//                                if (next != null) {
//                                    queue.tail.compareAndSet(curTail, next)
//                                }
//                            }
                        }
                    } else {
                        it.resume(element)
                    }
                } ?: return
            } else if (tail is Receiver<*>) {
                val receiver = queue.dequeue()
                if (receiver != null) {
                    receiver.continuation?.resume(element)
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
            val head = queue.head.value
            val tail = queue.tail.value

            if (head == tail || tail is Receiver<*>) {
                val result = suspendCoroutine<Any?> {
                    if (queue.tail.value == tail) {
                        val operation = Receiver<E>(null, it)
//                        queue.enqueue(Receiver<E>(null, it))

                        if (tail.next.compareAndSet(null, operation)) {
                            queue.tail.compareAndSet(tail, operation)
                        } else {
//                            if (tail.next.value is Sender<*>) {
//                                val sender = queue.dequeue()
//                                sender?.continuation?.resume(null)
//                            } else {
                                val next = tail.next.value
                                if (next != null) {
                                    queue.tail.compareAndSet(tail, next)
                                }
                                it.resume(null)
//                            }
                        }
                    } else {
                        it.resume(null)
                    }
                }
                if (result != null) {
                    return result as E
                }
            } else if (tail is Sender<*>) {
                val sender = queue.dequeue()
                if (sender != null) {
                    sender.continuation?.resume(null)
                    return (sender as Sender<*>).element as E
                }
            }
        }
    }
}

