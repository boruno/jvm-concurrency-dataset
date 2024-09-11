import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.lang.Exception
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    val head: AtomicRef<Node<E>?> = atomic(null)
    val tail: AtomicRef<Node<E>?> = atomic(null)
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val currentHead = head.value
            val currentTail = tail.value
            val headNext = currentHead!!.next.value

            if (currentTail == null) return

            if (headNext == null || headNext.type == Type.SENDER) {
                if (enqueue(element, currentTail, Type.SENDER).isSuccess)
                    return
                continue
            }

            if (dequeue(element, currentHead, Type.SENDER) != null)
                return
        }
    }

    suspend fun enqueue(value: E?, node: Node<E>, type: Type): Result<E> {
        return suspendCoroutine { coroutine ->
            val new = Node(value, coroutine as Continuation<Any?>, type)

            if (node.next.compareAndSet(null, new)) {
                tail.compareAndSet(node, new)
                coroutine.resumeWith(Result.success(true))
            }
            tail.compareAndSet(node, node.next.value)
            coroutine.resumeWith(Result.failure(Exception()))
        }
    }

    fun dequeue(element: E?, currentHead: Node<E>, type: Type): Node<E>? {
        val next = currentHead.next.value ?: return null;
        next.type = type
        if (head.compareAndSet(currentHead, next)) {
            return next
        }
        return null
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val currentHead = head.value
            val currentTail = tail.value!!
            val headNext = currentHead!!.next.value

            if (headNext == null || headNext.type == Type.RECEIVER) {
                val result = enqueue(null, currentTail, Type.RECEIVER)
                if (result.isFailure) continue
                return result.getOrDefault(currentHead.x!!)
            }
            return dequeue(null, currentHead, Type.RECEIVER)?.x ?: continue
        }
    }
}

class Node<E> (val x: E?, val continuation: Continuation<Any?>?, var type: Type) {
    val next = atomic<Node<E>?>(null)
}

enum class Type {
    DUMMY,
    SENDER,
    RECEIVER
}