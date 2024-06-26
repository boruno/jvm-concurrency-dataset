@file:Suppress("UNCHECKED_CAST")

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.*

const val RETRY = ""

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    private inner class Node(type: String? = null, element: E? = null) {
        val continuation: AtomicRef<Continuation<Any?>?> = atomic(null)
        val type: AtomicRef<String?> = atomic(type)
        val next: AtomicRef<Node?> = atomic(null)
        val element: AtomicRef<E?> = atomic(element)
    }

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
            val head = head.value
            val tail = tail.value
            val node = Node("SEND", element)

            if ((tail == head || tail.type.value == "SEND") && enqueueAndSuspend(tail, node)) {
                return
            } else if (dequeueAndResume(head, element)) {
                return
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val head = head.value
            val tail = tail.value

            val node = Node("RECEIVE", null)

            if (tail == head || tail.type.value == "RECEIVE") {
                if (enqueueAndSuspend(tail, node)) {
                    return node.element.value as E
                }

            } else {
                if (tail == this.tail.value) {
                    if (dequeueAndResume(head, null)) {
                        val headNext = head.next.value ?: throw IllegalStateException()
                        return headNext.element.value as E
                    }
                }
            }
        }
    }

    private suspend fun enqueueAndSuspend(tail: Node, node: Node): Boolean {
        val result = suspendCoroutine<Any?> action@{ process ->
            node.continuation.value = process
            if (!tail.next.compareAndSet(null, node)) {
                this.tail.compareAndSet(tail, tail.next.value!!)
                process.resume(RETRY)
                return@action
            } else {
                this.tail.compareAndSet(tail, node)
            }
        }
        return result != RETRY
    }

    private fun dequeueAndResume(head: Node, element: E?): Boolean {
        val headNext = head.next.value ?: return false

        return if (this.head.compareAndSet(head, headNext)) {
            if (element != null) {
                headNext.element.value = element
            }
            headNext.continuation.value?.resume(null)
            true
        } else {
            false
        }
    }
}


