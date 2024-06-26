/*import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
*/
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SynchronousQueue<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val newNode: Node<E> = Node(NodeType.SENDER, null)
        head = AtomicReference(newNode)
        tail = AtomicReference(newNode)
    }

    suspend fun send(element: E) {
        val node = Node(NodeType.SENDER, element)
        var curHead: Node<E>
        var curTail: Node<E>

        while (true) {
            curHead = head.get()
            curTail = tail.get()
            if (curTail.isSender() || curHead == curTail) {
                if (!check(curTail, node)) continue
                return
            }
            val next = curHead.next.get()
            if (next == null || curTail != tail.get() || curHead != head.get() || curHead == tail.get())
                continue
            if (next.isReceiver() && next.continuation !== null && head.compareAndSet(curHead, next)) {
                next.value.compareAndSet(null, element)
                next.continuation!!.resume(true)
                return
            }
        }
    }

    suspend fun receive(): E {
        val node: Node<E> = Node(NodeType.RECEIVER, null)
        var curHead: Node<E>
        var curTail: Node<E>
        while (true) {
            curHead = head.get()
            curTail = tail.get()
            if (curTail.isReceiver() || curHead == curTail) {
                if (!check(curTail, node)) continue
                return node.value.get()!!
            }
            val next = curHead.next.get()
            if (next == null || curHead == tail.get() || curTail != tail.get() || curHead != head.get())
                continue
            val element = next.value.get() ?: continue
            if (next.continuation !== null && head.compareAndSet(curHead, next)) {
                next.value.compareAndSet(element, null)
                next.continuation!!.resume(true)
                return element
            }
        }
    }

    private suspend fun check(curTail: Node<E>, node: Node<E>): Boolean {
        return suspendCoroutine<Boolean> sc@{ continuation ->
            node.continuation = continuation
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
            } else {
                continuation.resume(false)
                return@sc
            }
        }
    }

    class Node<T>(private val type: NodeType, v: T?) {
        val value = AtomicReference(v)
        val next: AtomicReference<Node<T>?> = AtomicReference(null)
        var continuation: Continuation<Boolean>? = null

        fun isSender(): Boolean { return type == NodeType.SENDER}
        fun isReceiver(): Boolean { return type == NodeType.RECEIVER}
    }

    enum class NodeType {
        SENDER,
        RECEIVER
    }
}