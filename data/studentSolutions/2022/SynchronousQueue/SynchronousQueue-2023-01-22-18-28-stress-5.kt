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
        val head = queue.head.value
        while (true) {
            if(head.next.value == null || head.next.value is SenderNode) {
                if(queue.enqueueSender(element)) {
                   return
                }
            } else {
                if(queue.dequeueSender(element)) {
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
        val head = queue.head.value
        while (true) {
            if(head.next.value == null || head.next.value is ReceiverNode) {
                val res = queue.enqueueReceiver()
                if(res.second) {
                    return res.first as E
                }
            } else {
                val res = queue.dequeueReceiver()
                if(res.second) {
                    return res.first
                }
            }
        }
    }
}
// Nikolay Rulev
class MSQueue<E : Any> {
    val head: AtomicRef<Node>
    val tail: AtomicRef<Node>

    init {
        val dummy = BaseNode() // TODO: check that it is no matter what it is
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */

    suspend fun enqueueSender(x: E): Boolean {
        val curTail = tail.value
        return suspendCoroutine { cont ->
            val next = curTail.next.value
            if (curTail == tail.value) {
                if (next == null) {
                    val newNode = SenderNode(x, cont)
                    if (curTail.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(curTail, newNode)
                    } else {
                        cont.resume(false)
                    }
                } else {
                    tail.compareAndSet(curTail, next)
                    cont.resume(false)
                }
            } else {
                cont.resume(false)
            }
        }
    }

    suspend fun enqueueReceiver(): Pair<Any, Boolean> {
        val curTail = tail.value
        val needRestart = Pair(Any(), false)
        return suspendCoroutine { cont ->
            val next = curTail.next.value
            if (curTail == tail.value) {
                if (next == null) {
                    val newNode = ReceiverNode(cont)
                    if (curTail.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(curTail, newNode)
                    } else {
                        cont.resume(needRestart)
                    }
                } else {
                    tail.compareAndSet(curTail, next)
                    cont.resume(needRestart)
                }
            } else {
                cont.resume(needRestart)
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    /*
       fun dequeue(): E? {
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
                        return next.x
                    }
                }

            }
        }
    }
     */
    fun dequeueReceiver(): Pair<E, Boolean> {
        val curHead = head.value
        val curTail = tail.value
        val next = curHead.next.value
        val needRestart = Pair(Any() as E, false)
        if (curHead == head.value) {
            if(curHead == curTail) {
                if (next != null) {
                    // CAS(&Q–>Tail, tail, <next.ptr, tail.count+1>)
                    tail.compareAndSet(curTail, next)
                }
                return needRestart
            } else {
                // CAS(&Q–>Head, head, <next.ptr, head.count+1>)
                if (next !is SenderNode) {
                    return needRestart
                }
                if (head.compareAndSet(curHead, next)) {
                    next.cont.resume(true)
                    return Pair(next.x as E, true)
                } else {
                    return needRestart
                }
            }
        } else {
            return needRestart
        }
    }

    fun dequeueSender(x: E): Boolean {
        val curHead = head.value
        val curTail = tail.value
        val next = curHead.next.value
        val needRestart = false
        if (curHead == head.value) {
            if(curHead == curTail) {
                if (next != null) {
                    // CAS(&Q–>Tail, tail, <next.ptr, tail.count+1>)
                    tail.compareAndSet(curTail, next)
                }
                return needRestart
            } else {
                // CAS(&Q–>Head, head, <next.ptr, head.count+1>)
                if (next !is ReceiverNode) {
                    return needRestart
                }
                if (head.compareAndSet(curHead, next)) {
                    next.cont.resume(Pair(x as Any, true))
                    return true
                } else {
                    return needRestart
                }
            }
        } else {
            return needRestart
        }
    }

    fun isEmpty(): Boolean {
        return head.value == tail.value
    }
}


sealed class Node {
    val next = atomic<Node?>(null)
}

class BaseNode: Node()
class SenderNode(val x: Any, val cont: Continuation<Boolean>): Node()
class ReceiverNode(val cont: Continuation<Pair<Any, Boolean>>): Node()