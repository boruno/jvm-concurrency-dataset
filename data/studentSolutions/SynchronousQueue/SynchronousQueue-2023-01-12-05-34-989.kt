import kotlinx.atomicfu.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>?>
    private val tail: AtomicRef<Node<E>?>

    init {
        val dummy = Node<E>(null, false)
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
            val curHead = head.value
            if (curTail == curHead || curTail!!.isSender) {
                val res = suspendCoroutine<Any> sc@ {cont ->
                    val newNode = Node<E>(element, true, cont)
                    if (curTail!!.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(curTail, newNode)
                    } else {
                        cont.resume("RETRY")
                        return@sc
                    }
                }
                if (res != "RETRY") {
                    return
                }
            } else {
                val headNext = head.value!!.next.value
                if (headNext != null && !headNext.isSender) {
                    if (head.compareAndSet(curHead, headNext)) {
                        headNext.cont!!.resume(element as Any)
                        return
                    }
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
            if (curHead == curTail || !curTail!!.isSender) {
                val res = suspendCoroutine<Any> sc@ {cont ->
                    val newNode = Node<E>(null, false, cont)
                    if (curTail!!.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(curTail, newNode)
                    } else {
                        cont.resume("RETRY")
                        return@sc
                    }
                }
                if (res != "RETRY") {
                    return res as E
                }
            } else {

                val headNext = head.value!!.next.value
                if (headNext != null && headNext.isSender) {
                    if (head.compareAndSet(curHead, headNext)) {
                        headNext.cont!!.resume(Unit)
                        return headNext.x as E
                    }
                }
            }
        }
    }
}

class Node<E>(val x: E?, val isSender: Boolean, var cont: Continuation<Any>? = null) {
    val next = atomic<Node<E>?>(null)
}