/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */

import kotlin.coroutines.Continuation
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.*


sealed class Node<E> {
    val next = atomic<Node<E>?>(null)

    class Dummy<E> : Node<E>()
    class Send<E>(val value: E, val cont: Continuation<Unit>) : Node<E>()
    class Receive<E>(val cont: Continuation<E>) : Node<E>()
}


/*
send(2)
send(2), send(3)
wait() -> send(2)
wait() -> []
wait() -> wait()
wait() -> wait()  wait()
send(4) -> wait()
 */

class SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node.Dummy<E>()
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */

    suspend fun send(element: E): Unit {
        while (true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value
            val curTail = tail.value
            if (curHead != curTail &&  curHeadNext is Node.Receive<E>) {
                if (head.compareAndSet(curHead, curHeadNext)) {
                    curHeadNext.cont.resume(element)
                    return
                }
                curHeadNext.cont.resume(element)
            }

            val fromNode = suspendCoroutine { cont ->
                val sendNode = Node.Send(element, cont)
                if (curTail is Node.Send<E>) {
                    if (curTail.next.compareAndSet(null, sendNode)) {
                        tail.compareAndSet(curTail, sendNode)
                    } else {
                        tail.compareAndSet(curTail, curTail.next.value as Node<E>)
                        cont.resume(false)
                    }
                } else {
                    cont.resume(false)
                }
            }
            if (fromNode != false) {
                return
            }
        }
    }
/*
    suspend fun send(element: E): Unit {
        while (true) {
            val curHead = head.value
            val cur_head_next = curHead.next
            if (cur_head_next != null) {
                val (_, cont) = curHead.value
                when (cont) {
                    is QueueContinuation.Receive -> {
                        if head.compareAndSet(curHead, cur_head_next) {
                            cont.cont.resume(element)
                            return
                        }
                        continue
                    }
                    else -> {}
                }
            }

            val Node<E>
        }
    }

    suspend fun send(element: E): Unit {
            suspendCoroutine {cont -> {
                while (true) {
                    val node = Node<E>(Pair(element, cont))
                    val cur_tail = tail.value
                    if (cur_tail.next.compareAndSet(null, node)) {
                        tail.compareAndSet(cur_tail, node)
                        break
                    } else {
                        tail.compareAndSet(cur_tail, cur_tail.next.value as Node<E>)
                    }
                }
            }}
    }
*/


    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value
            val curTail = tail.value
            if (curHead != curTail && curHeadNext is Node.Send<E>) {
                if (head.compareAndSet(curHead, curHeadNext)) {
                    curHeadNext.cont.resume(Unit)
                    return curHeadNext.value
                }
                curHeadNext.cont.resume(Unit)
            }

            val fromNode = suspendCoroutine { cont ->
                val receiveNode = Node.Receive<E>( cont)
                if (curTail is Node.Receive<E>) {
                    if (curTail.next.compareAndSet(null, receiveNode)) {
                        tail.compareAndSet(curTail, receiveNode)
                    } else {
                        tail.compareAndSet(curTail, curTail.next.value as Node<E>)
                        cont.resume(null)
                    }
                } else {
                    cont.resume(null)
                }
            }
            if (fromNode != null) {
                return fromNode
            }
        }
    }
}