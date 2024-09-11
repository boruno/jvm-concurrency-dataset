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
    private val queue: FAAQueue<Node<E>> = FAAQueue()
    private val sendIdx = atomic(0L)
    private val receiveIdx = atomic(0L)


    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val idx = sendIdx.getAndIncrement()
            if (idx < receiveIdx.value) {
                val curRes = queue.tryDequeue()
                if (curRes.first) {
                    val resNode = curRes.second
                    if (resNode != null) {
                        resNode.continuation.resume(Wrapper(element))
                        break
                    }
                }
            } else {
                val cuRes = suspendCoroutine<Wrapper<E>> sc@{ cont ->
                    val newNode = Node(element, cont)
                    if (!queue.tryEnqueue(newNode)) {
                        cont.resume(Wrapper(null))
                        return@sc
                    }
                }
                if (cuRes.element != null) break
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val idx = receiveIdx.getAndIncrement()
            if (idx < sendIdx.value) {
                val curRes = queue.tryDequeue()
                if (curRes.first) {
                    val resNode = curRes.second
                    if (resNode != null) {
                        resNode.continuation.resume(Wrapper(null))
                        return resNode.data!!
                    }
                }
            } else {
                val cuRes = suspendCoroutine<Wrapper<E>> sc@{ cont ->
                    val empty: E? = null
                    val newNode = Node(empty, cont)
                    if (!queue.tryEnqueue(newNode)) {
                        cont.resume(Wrapper(null))
                        return@sc
                    }
                }
                if (cuRes.element != null) return cuRes.element
            }
        }
    }
}

private class Node<E>(
    val data: E?,
    val continuation: Continuation<Wrapper<E>>
)

private class Wrapper<E>(val element: E?)