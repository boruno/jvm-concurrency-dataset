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
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    private val RETRY: Any
    private val STOP: Any

    init {
        val dummy = Node<E>(null, null)
        head = atomic(dummy)
        tail = atomic(dummy)
        RETRY = Any()
        STOP = Any()
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val curTail = tail.value
            val curHead = head.value
            if (curHead.next.value == null || curHead.next.value!!.value != null) {
                val res = suspendCoroutine sc@{ cont ->
                    val newNode = Node(element, cont)
                    if (shouldRetry(curTail, newNode)) {
                        cont.resume(RETRY)
                        return@sc
                    }
                }

                if (res == RETRY) {
                    continue
                } else {
                    return
                }
            } else {
                val res: Node<E>?
                val headNext = curHead.next.value!!
                if (head.compareAndSet(curHead, headNext)) {
                    res = headNext
                } else {
                    res = null
                }

                if (headNext != null) {
                    headNext.cont!!.resume(element)
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
            val curTail = tail.value
            val curHead = head.value
            if (curHead.next.value == null || curHead.next.value!!.value == null) {
                val res = suspendCoroutine { cont ->
                    val newNode = Node<E>(null, cont)
                    if (shouldRetry(curTail, newNode)) {
                        cont.resume(RETRY)
                    }
                }

                if (res == RETRY) {
                    continue
                }

                return res as E
            } else {
                val headNext = curHead.next.value!!
                if (head.compareAndSet(curHead, headNext)) {
                    headNext.cont!!.resume(STOP)
                    return headNext.value as E
                }
            }
        }
    }

    fun shouldRetry(curTail: Node<E>, node: Node<E>): Boolean {
        if (curTail.next.compareAndSet(null, node)) {
            tail.compareAndSet(curTail, node)
            return false
        } else {
            tail.compareAndSet(curTail, curTail.next.value!!)
            return true
        }
    }
}

class Node<E>(val value: E?, val cont: Continuation<Any?>?) {
    val next = atomic<Node<E>?>(null)
}