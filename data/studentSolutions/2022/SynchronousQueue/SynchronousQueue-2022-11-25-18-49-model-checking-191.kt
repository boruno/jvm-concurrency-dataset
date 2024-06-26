import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.*
import kotlin.random.Random

private class Node<E>() {

    var x: E? = null
    val next = atomic<Node<E>?>(null)
    val resumed = atomic(false)

    var sendThread: Continuation<Unit>? = null
    var receiveThread: Continuation<E>? = null
    var isSender : Boolean? = null


    // Sender
    constructor(t: Continuation<Unit>, x: E?) : this() {
        this.sendThread = t
        this.x = x
        this.isSender = true
    }

    // Receiver
    constructor(t: Continuation<E>) : this() {
        this.receiveThread = t
        this.isSender = false
    }

    fun isSender(): Boolean {
        return isSender == true
    }

    fun isDummy(): Boolean {
        return isSender == null
    }

    fun isReceiver(): Boolean {
        return isSender == false
    }

    fun resume(value : E?) : Boolean {
        if (isReceiver() && value == null) {
            throw IllegalArgumentException("You tried to wake receiver with null value");
        }
        if (isDummy()){
            return false
        }
        if (resumed.compareAndSet(false, true)) {
            if (isReceiver()) {
                receiveThread!!.resume(value!!)
            } else {
                sendThread!!.resume(Unit)
            }
            return true
        }
        return false
    }

}

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E : Any> {

    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>()
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {

        while (true) {
            val receiver = tryDequeueReceiver()
            if (receiver?.resume(element) == true) {
                return
            }

            var enqueued = false
            suspendCoroutine<Unit> { cont ->
                enqueued = tryEnqueueSender(element, cont)
                if (!enqueued) {
                    cont.resume(Unit)
                }
            }
            if (enqueued) {
                return
            }
        }

    }


    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val sender = tryDequeueSender()
            if (sender?.resume(null) == true) {
                return sender.x!!
            }

            var enqueued = false
            val r = suspendCoroutine<E?> { cont ->
                enqueued = tryEnqueueReceiver(cont)
                if (!enqueued) {
                    cont.resume(null)
                }
            }
            if (enqueued && r != null) {
                return r
            }
        }
    }








    private fun tryEnqueueSender(x: E, t: Continuation<Unit>) : Boolean {
        val node = Node(t, x)
        while (true) {
            val currentTail = tail.value
            if (currentTail.isReceiver()) {
                return false
            }
            if (currentTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(currentTail, node)
                return true
            } else {
                tail.compareAndSet(currentTail, currentTail.next.value!!)
            }
        }
    }

    private fun tryEnqueueReceiver(t: Continuation<E>) : Boolean {
        val node = Node(t)
        while (true) {
            val currentTail = tail.value
            if (currentTail.isSender()) {
                return false
            }
            if (currentTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(currentTail, node)
                return true
            } else {
                tail.compareAndSet(currentTail, currentTail.next.value!!)
            }
        }
    }

    private fun tryDequeueSender(): Node<E>? {
        while (true) {
            val currentHead = head.value
            val currentHeadNext = currentHead.next.value
            if (currentHeadNext == null) {
                return null
            }
            if (!currentHeadNext.isSender()) {
                return null
            }
            if (head.compareAndSet(currentHead, currentHeadNext)) {
                return currentHeadNext
            }
        }
    }

    private fun tryDequeueReceiver(): Node<E>? {
        while (true) {
            val currentHead = head.value
            val currentHeadNext = currentHead.next.value
            if (currentHeadNext == null) {
                return null
            }
            if (!currentHeadNext.isReceiver()) {
                return null
            }
            if (head.compareAndSet(currentHead, currentHeadNext)) {
                return currentHeadNext
            }
        }
    }

}