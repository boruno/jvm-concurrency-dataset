import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private class Retry {}

private val RETRY = Retry()

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null, null, 'd')
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private fun enqueue(node: Node<E>, curTail: Node<E>): Boolean {
        if (curTail.next.compareAndSet(null, node)) {
            tail.compareAndSet(curTail, node)
            return true
        }
        curTail.next.value?.let { tail.compareAndSet(curTail, it) }
        return false
    }

    private fun dequeue(curHead: Node<E>): Any? {
        val curHeadNext = curHead.next.value ?: return null
        if (head.compareAndSet(curHead, curHeadNext)) {
            return (curHeadNext.cont to curHeadNext.element)
        }
        return null
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
//    @Suppress("UNCHECKED_CAST")
    suspend fun send(element: E): Unit {
//        while (true) {
        val id = ThreadLocalRandom.current().nextInt(10000)
        for (aaa in 1..1000) {
            val t = tail.value
            val h = head.value

            if (h.next.value == null || t.type == 's') {
                val res = suspendCoroutine<Any> sc@{ cont ->
                    if (!enqueue(Node(cont, element, 's'), t)) {
                        cont.resume(RETRY)
                        return@sc
                    }
                }
                if (res == RETRY) {
                    continue
                }
                return
            } else {
                val deq = dequeue(h) ?: continue
                val (cont, _) = deq as Pair<Continuation<E>, Any?>
                cont.resume(element)
                return
            }
        }
        throw Exception("SEND EXCEPTION")
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
//    @Suppress("UNCHECKED_CAST")
    suspend fun receive(): E {
//        while (true) {
        for (aaa in 1..1000) {
            val t = tail.value
            val h = head.value

            if (h.next.value == null || t.type == 'r') {
                val res = suspendCoroutine<Any> sc@{ cont ->
                    if (!enqueue(Node(cont, null, 'r'), t)) {
                        cont.resume(RETRY)
                        return@sc
                    }
                }
                if (res == RETRY) {
                    continue
                }
                return res as E
            } else {
                val deq = dequeue(h) ?: continue
                val (cont, elem) = deq as Pair<Continuation<Unit>, E>
                cont.resume(Unit)
                return elem
            }
        }
        throw Exception("RECEIVE EXCEPTION")
    }
}

/*
type:
    s - send
    r - receive
    d - dummy
 */
private class Node<E>(val cont: Any?, val element: E?, val type: Char) {
    val next = atomic<Node<E>?>(null)
}