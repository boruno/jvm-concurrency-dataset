import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.*
import kotlin.random.Random

private class Node<E>() {

    var senderRnd = 0

    var sendThread: Continuation<Continuation<E>?>? = null
    var receiveThread: Continuation<E>? = null
    var isSender : Boolean? = null


    // Sender
    constructor(t: Continuation<Continuation<E>?>, x: E?) : this() {
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
                // anyone, i'm waiting for a receiver
                val receiver = suspendCoroutine<Continuation<E>?> { cont ->
                    val node = Node(cont, element)
                    node.senderRnd = Random.nextInt(100)
                    println("haaaah:" + node.senderRnd)
                    if (t.next.compareAndSet(null, node)) {
                        tail.compareAndSet(t, node)
                    }
                }
                print("lal")
                // after receiver kindly asks sender to stop and let him go after
                suspendCoroutine { cont ->
                    assert(receiver != null)
                    receiver!!.resume(element)
                    cont.resume(Unit)
                }
                // i'm done -- goodbye, folks
                print("love")
                return
            } else {
                // gotta tell everyone i took the receiver
                val currentHeadNext = h.next.value
                if (currentHeadNext != null) {
                    if (head.compareAndSet(h, currentHeadNext)) {
                        done = true
                        // receiver,you have a value
                        suspendCoroutine { cont ->
                            //currentHeadNext.sendThread = cont
                            print(currentHeadNext.receiveThread!!.hashCode())
                            currentHeadNext.receiveThread!!.resume(element)
                            cont.resume(Unit)
                        }
                        // and i'm leaving now
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
                // i'm waiting for a vacant sender
                var done : Boolean = false
                var node : Node<E>? = null
                return suspendCoroutine<E> { cont ->
                    node = Node<E>(cont)
                    if (t.next.compareAndSet(null, node)) {
                        tail.compareAndSet(t, node!!)
                        done = true
                    }
                }
//                //send
//                assert(done)
//
//                // i have received a value from sender
//                // i can finish now
//
//                // (done) {
//                    res = r
//                    return res
//                //}
            } else {
                val currentHeadNext = h.next.value
                if (currentHeadNext != null) {
                    if (head.compareAndSet(h, currentHeadNext)) {
                        // i have found a vacant sender
                        // gotta notify him to finish first
                        //val s = currentHeadNext.senderRnd
                        println("heh: " + currentHeadNext.senderRnd)
                        return suspendCoroutine<E> {  cont ->
                            //val o = currentHeadNext.sendThread!!.hashCode()
                            //print(h.sendThread == null)
                            //print(currentHeadNext.sendThread == null)
                            assert(h.sendThread == null)
                            currentHeadNext.sendThread!!.resume(cont)
                        }
                        //return r
                        // then sender lets me finish as well
//                        res = currentHeadNext.x!!
//                        return res
                    }
                }
            }
        }
        return res
    }



}