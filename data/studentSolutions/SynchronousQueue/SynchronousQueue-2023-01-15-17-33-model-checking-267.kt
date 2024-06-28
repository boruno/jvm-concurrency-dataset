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
    private val sendersHead: AtomicRef<Node<Any>>
    private val sendersTail: AtomicRef<Node<Any>>
    private val retry = Retry()

    init {
        val dummy1 = Node<Any>(null)
        sendersHead = atomic(dummy1)
        sendersTail = atomic(dummy1)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
//        if (receivers.isNotEmpty()) {
//            val r = receivers.removeAt(0)
//            r.resume(element)
//        } else {
//            suspendCoroutine<Unit> { cont ->
//                senders.add(cont to element)
//            }
//        }
        while (true) {// добавить себя и заснуть
            // head указывает на dummy node
            val currTail = sendersTail.value
            val currHead = sendersHead.value.next.value // next node, first is dummy
            if (currHead != null && currHead.x is Receiver<*>) {
                if (sendersHead.value.next.compareAndSet(currHead, currHead.next.value)) {
                    (currHead.x as Receiver<E>).c.resume(element)
                    return
                } else {
                    continue
                }
            }
            val res = suspendCoroutine<Any> sc@ { cont ->
                val newNode = Node(Sender<E>(cont to element) as Any)
                if (currTail.next.compareAndSet(null, newNode)) {// мб есть смысл все же ставить current, а потом проверять, что он валидный
                    sendersTail.compareAndSet(currTail, newNode)
                } else {
                    currTail.next.value?.let { sendersTail.compareAndSet(currTail, it) }
                    cont.resume(retry)
                    return@sc
                }
            }
            if (res == retry) continue else {
                return
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {// добавить себя и заснуть
            // validate head
            val currTail = sendersTail.value
            val currHead = sendersHead.value.next.value // next node, first is dummy
            if (currHead != null && currHead.x is Sender<*>) {
                if (sendersHead.value.next.compareAndSet(currHead, currHead.next.value)) {
                    (currHead.x as Sender<E>).c.first.resume(Unit)
                    return (currHead.x as Sender<E>).c.second
                } else {
                    continue
                }
            }
            val res = suspendCoroutine<Any> sc@ { cont ->
                val newNode = Node(Receiver<Any>(cont) as Any)
                if (currTail.next.compareAndSet(null, newNode)) {
                    sendersTail.compareAndSet(currTail, newNode)
                } else {
                    currTail.next.value?.let { sendersTail.compareAndSet(currTail, it) }
                    cont.resume(retry)
                    return@sc
                }
            }
            if (res == retry) continue else {
                return res as E
            }
        }
    }

    class Retry {}

    class Receiver<E>(val c: Continuation<E>) {}
    class Sender<E>(val c: Pair<Continuation<Unit>, E>) {}
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}
//package mpp.msqueue
//
//import kotlinx.atomicfu.AtomicRef
//import kotlinx.atomicfu.atomic
//
//class MSQueue<E> {
//    private val head: AtomicRef<Node<E>>
//    private val tail: AtomicRef<Node<E>>
//
//    init {
//        val dummy = Node<E>(null)
//        head = atomic(dummy)
//        tail = atomic(dummy)
//    }
//
//    /**
//     * Adds the specified element [x] to the queue.
//     */
//    fun enqueue(x: E) {
//        val newElement = Node(x)
//
//        while (true) {
//            val currTail = tail.value
//            if (tail.value.next.compareAndSet(null, newElement)) {
//                tail.compareAndSet(currTail, newElement)
//                return
//            } else {
//                currTail.next.value?.let { tail.compareAndSet(currTail, it) }
//            }
//        }
//    }
//
//    /**
//     * Retrieves the first element from the queue
//     * and returns it; returns `null` if the queue
//     * is empty.
//     */
//    fun dequeue(): E? {
//        while (true) {
//            val currHead = head.value
//
//            if (currHead.next.value != null) {
//                val currHeadNext = currHead.next.value
//
//                if (currHeadNext?.let { head.compareAndSet(currHead, it) } == true) {
//                    return currHeadNext.x
//                }
//            } else {
//                return null
//            }
//        }
//    }
//
//    fun isEmpty(): Boolean {
//        val currHead = head.value
//        return currHead.next.value == null
//    }
//}
//
//private class Node<E>(val x: E?) {
//    val next = atomic<Node<E>?>(null)
//}