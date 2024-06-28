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
            val h = head.value
            val t = tail.value
            val newNode = Node("SEND", element)

            if (t == h || t.type.value == "SEND")
                if (getResult(t, newNode) != RETRY)
                    return
                else if (dequeueAndResume(h, element))
                    return

        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val h = head.value
            val t = tail.value

            val newNode = Node("RECEIVE", null)

            if (t == h || t.type.value == "RECEIVE") {
                if (getResult(t, newNode) != RETRY)
                    return newNode.element.value as E
            } else {
                if (t == this.tail.value) {
                    if (dequeueAndResume(h, null)) {
                        val headNext = h.next.value ?: throw IllegalStateException()
                        return headNext.element.value as E
                    }
                }
            }
        }
    }

    private suspend fun getResult(t: Node, node: Node): Any? {
        val result = suspendCoroutine cs@{ process ->
            node.continuation.value = process

            if (!t.next.compareAndSet(null, node)) {
                this.tail.compareAndSet(t, t.next.value!!)
                process.resume(RETRY)
                return@cs
            } else
                this.tail.compareAndSet(t, node)
        }

        return result
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


