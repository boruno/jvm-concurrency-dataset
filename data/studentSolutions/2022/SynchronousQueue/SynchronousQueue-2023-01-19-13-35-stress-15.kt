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
    val queue = MSQueue<Coroutine<E>>()

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        val cor = queue.dequeue()
        if (cor != null) {
            if (cor.isReciever) {
                cor.receiver?.resume(element)
            }
            if (cor.isSender) {
                queue.enqueue(cor)
                val curCor = Coroutine<E>()
                suspendCoroutine<Unit> { cont ->
                    run {
                        curCor.setSenderCoroutine(cont to element)
                        queue.enqueue(curCor)
                    }
                }
            }
        }
        else {
            val curCor = Coroutine<E>()
            suspendCoroutine<Unit> { cont ->
                run {
                    curCor.setSenderCoroutine(cont to element)
                    queue.enqueue(curCor)
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val cor = queue.dequeue()
        if (cor != null) {
            if (cor.isSender) {
                cor.sender?.first?.resume(Unit)
                return cor.sender?.second!!
            }
            else {
                queue.enqueue(cor)
                val curCor = Coroutine<E>()
                return suspendCoroutine { cont ->
                    run {
                        curCor.setRecieverCoroutine(cont)
                        queue.enqueue(curCor)
                    }
                }
            }
        }
        else {
            val curCor = Coroutine<E>()
            return suspendCoroutine { cont ->
                run {
                    curCor.setRecieverCoroutine(cont)
                    queue.enqueue(curCor)
                }
            }
        }
    }
}

class Coroutine<E>() {
    var isSender = false
    var sender: Pair<Continuation<Unit>, E>? = null

    var isReciever = false
    var receiver: Continuation<E>? = null

    fun setSenderCoroutine(sender: Pair<Continuation<Unit>, E>) {
        this.sender = sender
        isSender = true
    }

    fun setRecieverCoroutine(reciever: Continuation<E>) {
        this.receiver = reciever
        isReciever = true
    }
}

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    fun enqueue(x: E) {
        while (true) {
            var node = Node(x);
            var cur_tail = this.tail.value;
            if (cur_tail.next.compareAndSet(null, node)) {
                tail.compareAndSet(cur_tail, node);
                return;
            }
            else {
                cur_tail.next.value?.let { tail.compareAndSet(cur_tail, it) }
            }
        }
    }

    fun dequeue(): E? {
        while (true) {
            var cur_head = this.head.value;
            var cur_head_next = cur_head.next;
            if (cur_head_next.value == null) {
                return null
            }
            var cur_head_next_value = cur_head_next.value;
            if (cur_head_next_value != null) {
                if (this.head.compareAndSet(cur_head, cur_head_next_value)) {
                    return cur_head_next_value.x;
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        if (this.head.value.next.value == null) {
            return true;
        }
        return false;
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}