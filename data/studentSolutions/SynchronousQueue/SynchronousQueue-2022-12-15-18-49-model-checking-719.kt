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
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null, Operation.NOTHING)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        println("${Thread.currentThread().id}: send $element ")
        while (true) {
            val curTail = tail.value
            val curHead = head.value
            if (curHead == curTail || curTail.isSender()) {
                println("${Thread.currentThread().id}: send suspend")
                enqueueAndSuspend(element, Operation.SEND)
            } else {
                dequeueAndResume()
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        println("${Thread.currentThread().id}: receive")
        while (true) {
            val curTail = tail.value
            val curHead = head.value
            if (curHead == curTail || curTail.isReceiver()) {
                println("${Thread.currentThread().id}: receive is suspend")
                enqueueAndSuspend(null, Operation.RECEIVE)
            } else {
                dequeueAndResume()
                println("${Thread.currentThread().id}: receive result ${curHead.next.value?.x!!}")
                return curHead.next.value?.x!!
            }
        }
    }

    private fun dequeueAndResume(): E? {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            val curHeadNext = curHead.next.value
            if (curHead == curTail) {
                if (curHeadNext == null) {
                    return null
                } else {
                    println("${Thread.currentThread().id}: dequeue try move tail before: ${tail.value.x}")
                    tail.compareAndSet(curTail, curHeadNext)
                    println("${Thread.currentThread().id}: dequeue try move tail before: ${tail.value.x}")
                }
            } else if (curHeadNext?.let { head.compareAndSet(curHead, it) } == true) {
                curHead.continuation.value?.resume(null)
                return curHeadNext.x
            }
        }
    }

    private suspend fun enqueueAndSuspend(element: E?, operation: Operation) {
        suspendCoroutine action@{ process ->
            println("${Thread.currentThread().id}: suspend coroutine")
            while (true) {
                val node = Node(type = operation, x = element)
                val curTail = tail.value
                println("${Thread.currentThread().id}: suspend coroutine current tail: ${tail.value.x}")
                node.continuation.value = process
                if (curTail.next.compareAndSet(null, node)) {
                    println("${Thread.currentThread().id}: suspend coroutine try create new tail before: ${tail.value.x}")
                    tail.compareAndSet(curTail, node)
                    println("${Thread.currentThread().id}: suspend coroutine try create new tail after: ${tail.value.x}")
                    return@action
                } else {
                    println("${Thread.currentThread().id}: suspend coroutine try move tail before: ${tail.value.x}")
                    tail.compareAndSet(curTail, curTail.next.value!!)
                    println("${Thread.currentThread().id}: suspend coroutine try move tail after: ${tail.value.x}")
                }
            }
        }
    }
}

class Node<E> internal constructor(
    var x: E?,
    val type: Operation
) {
    val next: AtomicRef<Node<E>?> = atomic(null)
    val continuation: AtomicRef<Continuation<Any?>?> = atomic(null)
    fun isReceiver() = type == Operation.RECEIVE
    fun isSender() = type == Operation.SEND
}

enum class Operation {
    SEND, RECEIVE, NOTHING
}