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
class SynchronousQueue<E : Any> {
    val queue = MSQueue<E>()
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        queue.eliminate(Send(element))
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val res = queue.eliminate(Receive)
        return res!!
    }
}

sealed class TaskType
object Receive : TaskType()
class Send(val value: Any) : TaskType()

// Nikolay Rulev
class MSQueue<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node(Any(), Receive) // TODO: check that it is no matter what it is
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: Any, type: TaskType) {
        val newNode = Node(x, type)
        while (true) {
            val curTail = tail.value
            val next = curTail.next.value
            if (curTail == tail.value) {
                if (next == null) {
                    if(curTail.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(curTail, newNode)
                        break
                    }
                } else {
                    tail.compareAndSet(curTail, next)
                }
            }
        }
    }

    suspend fun eliminate(t: TaskType): E? {
        val curQRes = dequeue()
        if(curQRes == null) {
            return suspendCoroutine { cont -> enqueue(cont as Any, t) }
        } else if (curQRes.second is Receive && t is Send) {
            (curQRes.first as Continuation<E>).resume(t.value as E)
            return null
        } else if (curQRes.second is Send && t is Receive) {
            (curQRes.first as Continuation<Unit>).resume(Unit)
            return (curQRes.second as Send).value as E
        } else {
            enqueue(curQRes.first, curQRes.second)
            return suspendCoroutine { cont -> enqueue(cont as Any, t) }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): Pair<Any, TaskType>? {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            val next = curHead.next.value
            if (curHead == head.value) {
                if(curHead == curTail) {
                    if (next == null) {
                        return null
                    } else {
                        // CAS(&Q–>Tail, tail, <next.ptr, tail.count+1>)
                        tail.compareAndSet(curTail, next)
                    }
                } else {
                    // CAS(&Q–>Head, head, <next.ptr, head.count+1>)
                    if (head.compareAndSet(curHead, next!!)) {
                        return Pair(next.cont, next.type)
                    }
                }

            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value == tail.value
    }
}



private class Node(val cont: Any, val type: TaskType) {
    val next = atomic<Node?>(null)
}