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
                suspendCoroutine<Any?> {
                    if (queue.tail.value == tail) {
                        val operation = Sender<E>(element, it)

                        if (tail.next.compareAndSet(null, operation)) {
                            if (!queue.tail.compareAndSet(tail, operation)) {
                                it.resume(element)
                            }
                        } else {
                            val next = tail.next.value
                            if (next != null) {
                                queue.tail.compareAndSet(tail, next)
                            }
                            it.resume(element)
                        }
                    } else {
                        it.resume(element)
                    }
                } ?: return
            } else if (tail is Receiver<*>) {
//                val receiver = queue.dequeue()

                val curHead = queue.head.value
                val curTail = queue.tail.value
                val curHeadNext = curHead.next.value

                if (curHead == queue.head.value) {
                    if (curHead == curTail) {
                        if (curHeadNext != null) {
                            queue.tail.compareAndSet(curTail, curHeadNext)
                        }
                    } else {
                        if (curHeadNext != null) {
                            if (queue.head.compareAndSet(curHead, curHeadNext)) {
                                curHeadNext.continuation?.resume(element)
                                return
                            }
                        }
                    }
                }

//                if (receiver != null) {
//                    receiver.continuation?.resume(element)
//                    return
//                }
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
                        if (tail.next.compareAndSet(null, operation)) {
                            queue.tail.compareAndSet(tail, operation)
                        } else {
                            val next = tail.next.value
                            if (next != null) {
                                queue.tail.compareAndSet(tail, next)
                            }
                            it.resume(null)
                        }
                    } else {
                        it.resume(null)
                    }
                }
                if (result != null) {
                    return result as E
                }
            } else if (tail is Sender<*>) {
//                val sender = queue.dequeue()

                val curHead = queue.head.value
                val curTail = queue.tail.value
                val curHeadNext = curHead.next.value

                if (curHead == queue.head.value) {
                    if (curHead == curTail) {
                        if (curHeadNext != null) {
                            queue.tail.compareAndSet(curTail, curHeadNext)
                        }
                    } else {
                        if (curHeadNext != null) {
                            if (queue.head.compareAndSet(curHead, curHeadNext)) {
                                curHeadNext.continuation?.resume(null)
                                return (curHeadNext as Sender<*>).element as E
                            }
                        }
                    }
                }

//                if (sender != null) {
//                    sender.continuation?.resume(null)
//                    return (sender as Sender<*>).element as E
//                }
            }
        }
    }
}

