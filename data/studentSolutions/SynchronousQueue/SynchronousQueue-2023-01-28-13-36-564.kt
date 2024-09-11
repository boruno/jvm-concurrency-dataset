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
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    private val head: AtomicRef<Node<E>> = atomic(Node(null, null))
    private val tail: AtomicRef<Node<E>> = atomic(Node(null, null))

    class Node<E>(tip: String?, e: E?) {
        val next: AtomicRef<Node<E>?> = atomic(null)
        val elem = atomic(e)
        val cont: AtomicRef<Continuation<Boolean>?> = atomic(null)
        val act = atomic(tip)
    }

    suspend fun send(element: E) : Unit {
        while (true) {
            val valTail = tail.value
            val valHead = head.value
            if (valTail == valHead || valTail.act.value == "send") {
                val nods = Node("send", element)
                if (!cor(valTail, nods)) return
            } else {
                val nnod = valHead.next.value
                if (nnod != null && head.compareAndSet(valHead, nnod)) {
                    val nval = nnod.elem.value
                    nnod.elem.compareAndSet(nval, element)
                    nnod.cont.value!!.resume(false)
                    return
                }
            }
        }
    }

    suspend fun cor(t: Node<E>, nod: Node<E>): Boolean =
        suspendCoroutine { i ->
            nod.cont.value = i
            if (t.next.compareAndSet(null, nod))
                tail.compareAndSet(t, nod)
            else {
                tail.compareAndSet(t, t.next.value!!)
                i.resume(true)
            }
        }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val valTail = tail.value
            val valHead = head.value
            if (valTail == valHead || valTail.act.value == "unsend") {
                val nodr = Node<E>("unsend", null)
                if (!cor(valTail, nodr)) return nodr.elem.value!!
            } else {
                val nnod = valHead.next.value
                if (nnod != null && head.compareAndSet(valHead, nnod)) {
                    nnod.cont.value!!.resume(false)
                    return nnod.elem.value!!
                }
            }
        }
    }
}
