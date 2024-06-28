import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val RETRY: Int = 190

class SynchronousQueue<E>{

    private enum class Type {
        SEND, RECEIVE
    }

    private inner class Node(
        type: Type? = null,
        element: E? = null
    ) {
        val next: AtomicReference<Node?> = AtomicReference(null)
        val type: AtomicReference<Type?> = AtomicReference(type)
        val element: AtomicReference<E?> = AtomicReference(element)
        val continuation: AtomicReference<Continuation<Any?>?> = AtomicReference(null)

        fun isSender() = type.get() == Type.SEND
        fun isReceiver() = type.get() == Type.RECEIVE
    }

    private val dummy = Node()
    private var head: AtomicReference<Node> = AtomicReference(dummy)
    private var tail: AtomicReference<Node> = AtomicReference(dummy)

    suspend fun send(element: E) {
        while (true) {
            val head = this.head.get()
            val tail = this.tail.get()
            val node = Node(Type.SEND, element)
            if (tail == head || tail.isSender()) {
                val res = suspendCoroutine<Any?> sc@{ cont ->
                    node.continuation.set(cont)
                    if (!tail.next.compareAndSet(null, node)) {
                        cont.resume(RETRY)
                        return@sc
                    }
                    this.tail.compareAndSet(tail, node)
                }
                if (res != null) return
            } else {
                val n = head.next.get()
                if (tail != this.tail.get() || head != this.head.get() || n == null) {
                    continue
                }
                val success = this.head.compareAndSet(head, n)
                if (success) {
                    n.element.compareAndSet(null, element)
                    n.continuation.get()?.resume(null)
                    return
                }
            }
        }
    }

    suspend fun receive(): E {
        while (true) {
            val head = this.head.get()
            val tail = this.tail.get()
            val node = Node(Type.RECEIVE)
            if (tail == head || tail.isReceiver()) {
                val res = suspendCoroutine<Any?> sc@ { cont ->
                    node.continuation.set(cont)
                    if (!tail.next.compareAndSet(null, node)) {
                        cont.resume(RETRY)
                        return@sc
                    }
                    this.tail.compareAndSet(tail, node)
                }
                if (res != null) return res as E
            } else {
                val n = head.next.get()
                if (tail != this.tail.get() || head != this.head.get() || n == null) {
                    continue
                }
                val success = this.head.compareAndSet(head, n)
                if (success) {
                    n.continuation.get()?.resume(null)
                    return head.next.get()?.element?.get()!!
                }
            }
        }
    }
}