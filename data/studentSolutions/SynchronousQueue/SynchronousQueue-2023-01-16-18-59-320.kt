import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */

enum class NodeType {
    None, Send, Receiver
}

class Node<E>(
    val type: NodeType,
    val value: AtomicReference<E?> = AtomicReference(null),
    var cont: Continuation<Boolean>? = null,
    val next: AtomicReference<Node<E>> = AtomicReference(null)
)

class SynchronousQueue<E> {
    private val fake: Node<E> = Node(NodeType.None)
    private val head: AtomicReference<Node<E>> = AtomicReference(fake)
    private val tail: AtomicReference<Node<E>> = AtomicReference(fake)

    private suspend fun suspendCoroutineWithNode(node: Node<E>, t: Node<E>) =
        suspendCoroutine { cont ->
            node.cont = cont
            when {
                t.next.compareAndSet(null, node) -> tail.compareAndSet(t, node)
                else -> cont.resume(false)
            }
        }


    suspend fun send(element: E): Unit {
        while (true) {
            val t = tail.get()
            val h = head.get()
            when {
                (h == t || t.type != NodeType.Receiver) && suspendCoroutineWithNode(
                    Node(NodeType.Send, AtomicReference(element)),
                    t
                ) -> return
                else -> {
                    val next = h.next.get() ?: continue
                    if (next.type == NodeType.Receiver && head.compareAndSet(h, next)) {
                        next.value.compareAndSet(null, element)
                        next.cont!!.resume(true)
                        return
                    }
                }
            }
        }
    }

    suspend fun receive(): E {
        while (true) {
            val t = tail.get()
            val h = head.get()
            val node: Node<E> = Node(NodeType.Receiver)

            when {
                (h == t || t.type == NodeType.Receiver) && suspendCoroutineWithNode(
                    node,
                    t
                ) -> return node.value.get()!!
                else -> {
                    val next = h.next.get() ?: continue
                    val element = next.value.get() ?: continue
                    if (next.type != NodeType.Receiver && head.compareAndSet(h, next)) {
                        next.value.compareAndSet(element, null)
                        next.cont!!.resume(true)
                        return element
                    }
                }
            }
        }
    }
}