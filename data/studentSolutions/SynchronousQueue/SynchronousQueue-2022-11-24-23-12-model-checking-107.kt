import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.*

private class Node<E>() {

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

    var x: E? = null
    val next = atomic<Node<E>?>(null)

    fun isSender(): Boolean {
        return x != null
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
        var done = false
        while (!done) {

            val t = tail.value
            val h = head.value

            if (t == h || t.isSender()) {
                suspendCoroutine<Unit> { cont ->
                    val node = Node<E>(cont, element)
                    if (t.next.compareAndSet(null, node)) {
                        tail.compareAndSet(t, node)
                        done = true
                    }
                }
            } else {
                val currentHeadNext = h.next.value
                if (currentHeadNext != null) {
                    if (head.compareAndSet(h, currentHeadNext)) {
                        done = true
                        suspendCoroutine<Unit> { cont ->
                            currentHeadNext.sendThread = cont
                            currentHeadNext.receiveThread!!.resume(element)
                            cont.resume(Unit)
                        }
                        return

                    }
                }
            }

        }

    }


    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        var res : E? = null
        while (res == null) {
            val t = tail.value
            val h = head.value

            if (t == h || !t.isSender()) {
                var done : Boolean = false
                var node : Node<E>? = null
                val r = suspendCoroutine<E> { cont ->
                    node = Node<E>(cont)
                    if (t.next.compareAndSet(null, node)) {
                        tail.compareAndSet(t, node!!)
                        done = true
                    }
                }
                if (done) {

                    //async {}

//                    node!!.sendThread!!
//                    Con
//                    CoroutineScope.launch (Dispatchers.IO) {
//
//                        //do some background work
//                        ...
//                        withContext (Dispatchers.Main) {
//                            //update the UI
//                            button.isEnabled=true
//                            ...
//                        }
//                    }

                    node!!.sendThread!!.resume(Unit)
//                    runBlocking {
//                        launch {
//
//                        }
//                    }
//                    suspendCoroutine<E> { cont ->
//                        node!!.sendThread!!.resume(Unit)
//                    }
                    res = r
                    return res
                }
            } else {
                val currentHeadNext = h.next.value;
                if (currentHeadNext != null) {
                    if (head.compareAndSet(h, currentHeadNext)) {
                        currentHeadNext.sendThread!!.resume(Unit)
                        res = currentHeadNext.x!!
                    }
                }
            }
        }
        return res
    }



}