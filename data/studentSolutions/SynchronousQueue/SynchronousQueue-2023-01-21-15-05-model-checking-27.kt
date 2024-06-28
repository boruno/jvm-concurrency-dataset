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
    private val head: AtomicRef<Segment<E>>
    private val tail: AtomicRef<Segment<E>>

    init {
        val firstNode = Segment<E>(null, null)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        while (true) {
            val h = head.value
            val t = tail.value
            if (t == h || t.operationType == OperationType.SEND) {
                val new = Segment(element, OperationType.SEND)
                if (enqueueAndSuspend(t, new))
                    continue
                else
                    break
            } else {
                // dequeueAndResume(h)
                val next = h.next.value
                if (next?.coroutine == null) {
                    continue
                }
                if (head.compareAndSet(h, next)) {
                    next.element = element
                    next.coroutine?.resume(false)
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
            val t = tail.value
            val h = head.value
            if (t == h || t.operationType == OperationType.RECEIVE) {
                val new = Segment<E>(null, OperationType.RECEIVE)
                // new.coroutine = enqueueAndSuspend(t, new)
                if (enqueueAndSuspend(t, new))
                    continue
                else
                    return new.element!!
            } else {
                val next = h.next.value
                if (next?.coroutine == null) {
                    continue
                }
                if (head.compareAndSet(h, next)) {
                    next.coroutine?.resume(false)
                    return next.element!!
                }
            }
        }
    }

    private suspend fun enqueueAndSuspend(t: Segment<E>, new: Segment<E>): Boolean {
        return suspendCoroutine<Boolean> sc@{ cont ->
            new.coroutine = cont
            if (!t.next.compareAndSet(null, new)) {
                tail.compareAndSet(t, t.next.value!!) // иначе нет obstruction freedom, например, send(2) | send(6)
                cont.resume(true)
                return@sc
            }
            tail.compareAndSet(t, new) // никто другой уже не сможет добавить

        }
    }

}

enum class OperationType {
    SEND,
    RECEIVE
}

class Segment<E>(var element: E?, var operationType: OperationType?) {
    val next: AtomicRef<Segment<E>?> = atomic(null)
    var coroutine: Continuation<Boolean>? = null
}