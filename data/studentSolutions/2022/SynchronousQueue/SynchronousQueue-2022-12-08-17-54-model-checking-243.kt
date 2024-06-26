@file:Suppress("UNCHECKED_CAST")

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.*

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node()
        head = atomic(dummy)
        tail = atomic(dummy)
    }


    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val t = tail.value
            val h = head.value

            if (t == h || t.type.value == Operation.SEND) {
                val res = suspendCoroutine sc@{ cont ->
                    val newNode = Node(element, Operation.SEND)
                    newNode.continuation.value = cont

                    if (!t.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(t, t.next.value!!)
                        cont.resume("1")
                        return@sc
                    } else {
                        tail.compareAndSet(t, newNode)
                    }
                }

                if (res != null) return

            } else {
                if (t == tail.value) {
                    val headNext = h.next.value ?: continue

                    if (head.compareAndSet(h, headNext)) {
                        if (element != null)
                            headNext.element.value = element

                        headNext.continuation.value?.resume(null)
                    }

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
            val t = tail.value
            val h = head.value

            if (t == h || t.type.value == Operation.RECEIVE) {
                val newNode = Node(null, Operation.RECEIVE)

                val res = suspendCoroutine sc@{ cont ->
                    newNode.continuation.value = cont

                    if (!t.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(t, t.next.value!!)
                        cont.resume(null)
                        return@sc
                    } else {
                        tail.compareAndSet(t, newNode)
                    }
                }

                if (res != null) return newNode.element.value as E

            } else {
                if (t == tail.value) {
                    val headNext = h.next.value ?: continue

                    if (head.compareAndSet(h, headNext)) {
                        headNext.continuation.value?.resume(null)
                    }

                    return headNext.element.value as E
                }
            }
        }
    }

    private inner class Node(x: E? = null, type: Operation? = null) {
        val continuation: AtomicRef<Continuation<Any?>?> = atomic(null)
        val type: AtomicRef<Operation?> = atomic(type)
        val element: AtomicRef<E?> = atomic(x)
        val next: AtomicRef<Node?> = atomic(null)
    }

    private enum class Operation {
        SEND, RECEIVE
    }
}

