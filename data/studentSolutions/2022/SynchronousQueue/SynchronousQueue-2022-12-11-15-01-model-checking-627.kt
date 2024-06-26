import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.system.exitProcess

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E:Any> {

    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null, null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val curTail = tail.value
            if (isEmpty() || curTail.x != null) {
                val res = suspendCoroutine { cont ->
                    val newTail = Node(element, cont)
                    if (curTail.next.compareAndSet(null, newTail)) {
                        tail.compareAndSet(curTail, newTail)
                    } else {
                        tail.compareAndSet(curTail, curTail.next.value!!)
                        cont.resume(RETRY)
                    }
                }
                if (res !== RETRY)
                    break
            } else {
                val curHead = head.value
                val curHeadNext = curHead.next.value!!
                if (head.compareAndSet(curHead, curHeadNext)) {
                    curHeadNext.continuation!!.resume(element)
                    break
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
            val curTail = tail.value
            if (isEmpty() || curTail.x == null) {
                val res = suspendCoroutine { cont ->
                    val newTail = Node<E>(null, cont)
                    if (curTail.next.compareAndSet(null, newTail)) {
                        tail.compareAndSet(curTail, newTail)
                        return@suspendCoroutine
                    } else {
                        tail.compareAndSet(curTail, curTail.next.value!!)
                        cont.resume(RETRY)
                        return@suspendCoroutine
                    }
                }
                if (res !== RETRY)
                    return res as E
            } else {
                val curHead = head.value
                val curHeadNext = curHead.next.value!!
                if (head.compareAndSet(curHead, curHeadNext)) {
                    curHeadNext.continuation!!.resume(Unit)
                    return curHeadNext.x!!
                }
            }
        }
    }

    private fun isEmpty(): Boolean {
        val curHead = head.value
        return curHead.next.value == null
    }

    companion object {
        object RETRY
    }
}

private class Node<E>(val x: E?, val continuation: Continuation<Any>?) {
    val next = atomic<Node<E>?>(null)
}