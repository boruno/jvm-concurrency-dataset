import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.*

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {

    // private val senders   = MSQueue<Pair<Continuation<Unit>, E>>()
    // private val receivers = MSQueue<Continuation<E>>()
    private val queue = MSQueue<Either<E>>()

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val tail = queue.tail.value
            val head = queue.head.value
            val value = tail.x
            if (queue.isEmpty() || value!!.isSender()) {
                val res = suspendCoroutine<Boolean> sc@{ cont ->
                    val res2 = queue.enqueue(tail, Either<E>(0, cont to element, null))
                    if (res2 == false) {
                        cont.resume(false)
                        return@sc
                    }
                }
                if (res == true) return
            } else {
                val r = queue.dequeue(head)
                if (r != null) {
                    r.receiver!!.resume(element)
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
        while (true) {
            val tail = queue.tail.value
            val head = queue.head.value
            var value = tail.x
            if (queue.isEmpty() || value!!.isReceiver()) {
                val res = suspendCoroutine sc@{ cont ->
                    val res2 = queue.enqueue(tail, Either<E>(1, null, cont))
                    if (res2 == false) {
                        cont.resume(null)
                        return@sc
                    }
                }
                if (res != null) return res
            } else {
                val pair = queue.dequeue(head)
                if (pair != null) {
                    val (s, elem) = pair.sender!!
                    s.resume(true)
                    return elem
                }
            }
        }
    }

    private class Either<E>(val op: Int,
                            val sender:   Pair<Continuation<Boolean>, E>?,
                            val receiver: Continuation<E>?) {
        fun isSender()   = op == 0
        fun isReceiver() = op == 1
    }
}

class MSQueue<E> {
    val head: AtomicRef<Node<E>>
    val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    fun enqueue(tailNode: Node<E>, x: E): Boolean {
        val node = Node(x)
        if (tailNode.next.compareAndSet(null, node)) {
            tail.compareAndSet(tailNode, node)
            return true
        } else {
            val tailNodeNext = tailNode.next.value
            if (tailNodeNext != null) {
                tail.compareAndSet(tailNode, tailNodeNext)
            }
            return false
        }
    }

    fun dequeue(headNode: Node<E>): E? {
        val headNodeNext = headNode.next.value
        if (headNodeNext == null) {
            return null
        } else if (head.compareAndSet(headNode, headNodeNext)) {
            return headNodeNext.x
        } else {
            return null
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null
    }

    class Node<E>(val x: E?) {
        val next = atomic<Node<E>?>(null)
    }
}

    // org.jetbrains.kotlinx.lincheck.LincheckAssertionError: 
    // = The execution failed with an unexpected exception =
    // Execution scenario (parallel part):
    // | send(2) | send(6) | receive() |

    // java.lang.NullPointerException
    //     at SynchronousQueue.send(SynchronousQueue.kt:24)
    //     at SynchronousQueueTest.send(SynchronousQueueTest.kt:18)
    //     at org.jetbrains.kotlinx.lincheck.runner.TestThreadExecution48.run(Unknown Source)
    //     at org.jetbrains.kotlinx.lincheck.runner.FixedActiveThreadsExecutor$testThreadRunnable$1.run(FixedActiveThreadsExecutor.kt:173)
    //     at java.base/java.lang.Thread.run(Thread.java:829)


    // = The following interleaving leads to the error =
    // Parallel part trace:
    // | send(2)                                                                                        |                                                                                           |                      |
    // |   send(2): threw NullPointerException at SynchronousQueueTest.send(SynchronousQueueTest.kt:18) |                                                                                           |                      |
    // |     getValue(): Node@1 at SynchronousQueue.send(SynchronousQueue.kt:22)                        |                                                                                           |                      |
    // |     switch                     Node                                                                |                                                                                           |                      |
    // |                                                                                                | send(6)                                                                                   |                      |
    // |                                                                                                |   send(6): CoroutineSingletons@1 at SynchronousQueueTest.send(SynchronousQueueTest.kt:18) |                      |
    // |                                                                                                |     getValue(): Node@1 at SynchronousQueue.send(SynchronousQueue.kt:22)                   |                      |
    // |                                                                                                |     getValue(): Node@1 at SynchronousQueue.send(SynchronousQueue.kt:23)                   |                      |
    // |                                                                                                |     enqueue(Node@1,Either@1): true at SynchronousQueue.send(SynchronousQueue.kt:26)       |                      |
    // |                                                                                                |   switch (reason: coroutine is suspended)                                                 |                      |
    // |                                                                                                |                                                                                           | receive()            |
    // |                                                                                                |                                                                                           |   thread is finished |
    // |     getValue(): Node@2 at SynchronousQueue.send(SynchronousQueue.kt:23)                        |                                                                                           |                      |
    //     at app//org.jetbrains.kotlinx.lincheck.LinChecker.check(LinChecker.kt:50)
    //     at app//org.jetbrains.kotlinx.lincheck.LinChecker$Companion.check(LinChecker.kt:210)
    //     at app//org.jetbrains.kotlinx.lincheck.LinCheckerKt.check(LinChecker.kt:223)
    //     at app//SynchronousQueueTest.modelCheckingTest(SynchronousQueueTest.kt:35)


    // org.jetbrains.kotlinx.lincheck.LincheckAssertionError: 
    // = The execution failed with an unexpected exception =
    // Execution scenario (parallel part):
    // | send(2)  | send(6) | receive() |
    // | send(-6) |         |           |

    // java.lang.NullPointerException
    //     at SynchronousQueue.send(SynchronousQueue.kt:37)
    //     at SynchronousQueueTest.send(SynchronousQueueTest.kt:18)
    //     at org.jetbrains.kotlinx.lincheck.runner.TestThreadExecution39.run(Unknown Source)
    //     at org.jetbrains.kotlinx.lincheck.runner.FixedActiveThreadsExecutor$testThreadRunnable$1.run(FixedActiveThreadsExecutor.kt:173)
    //     at java.base/java.lang.Thread.run(Thread.java:829)


    // = The following interleaving leads to the error =
    // Parallel part trace:
    // |                                                                                            | send(6)                                                                                        |                      |
    // |                                                                                            |   send(6): threw NullPointerException at SynchronousQueueTest.send(SynchronousQueueTest.kt:18) |                      |
    // |                                                                                            |     getValue(): Node@1 at SynchronousQueue.send(SynchronousQueue.kt:22)                        |                      |
    // |                                                                                            |     switch                                                                                     |                      |
    // | send(2)                                                                                    |                                                                                                |                      |
    // |   send(2): CoroutineSingletons@1 at SynchronousQueueTest.send(SynchronousQueueTest.kt:18)  |                                                                                                |                      |
    // |     getValue(): Node@1 at SynchronousQueue.send(SynchronousQueue.kt:22)                    |                                                                                                |                      |
    // |     getValue(): Node@1 at SynchronousQueue.send(SynchronousQueue.kt:23)                    |                                                                                                |                      |
    // |     enqueue(Node@1,Either@1): true at SynchronousQueue.send(SynchronousQueue.kt:27)        |                                                                                                |                      |
    // |   switch (reason: coroutine is suspended)                                                  |                                                                                                |                      |
    // |                                                                                            |                                                                                                | receive()            |
    // |                                                                                            |                                                                                                |   thread is finished |
    // | send(-6)                                                                                   |                                                                                                |                      |
    // |   send(-6): CoroutineSingletons@1 at SynchronousQueueTest.send(SynchronousQueueTest.kt:18) |                                                                                                |                      |
    // |     getValue(): Node@2 at SynchronousQueue.send(SynchronousQueue.kt:22)                    |                                                                                                |                      |
    // |     getValue(): Node@2 at SynchronousQueue.send(SynchronousQueue.kt:23)                    |                                                                                                |                      |
    // |     enqueue(Node@2,Either@2): true at SynchronousQueue.send(SynchronousQueue.kt:27)        |                                                                                                |                      |
    // |   switch (reason: coroutine is suspended)                                                  |                                                                                                |                      |
    // |                                                                                            |     getValue(): Node@2 at SynchronousQueue.send(SynchronousQueue.kt:23)                        |                      |
    // |                                                                                            |     dequeue(Node@2): Either@2 at SynchronousQueue.send(SynchronousQueue.kt:35)                 |                      |
    // |                                                                                            |       getValue(): Node@3 at MSQueue.dequeue(SynchronousQueue.kt:106)                           |                      |
    // |                                                                                            |       compareAndSet(Node@2,Node@3): true at MSQueue.dequeue(SynchronousQueue.kt:109)           |                      |
    //     at app//org.jetbrains.kotlinx.lincheck.LinChecker.check(LinChecker.kt:50)
    //     at app//org.jetbrains.kotlinx.lincheck.LinChecker$Companion.check(LinChecker.kt:210)
    //     at app//org.jetbrains.kotlinx.lincheck.LinCheckerKt.check(LinChecker.kt:223)
    //     at app//SynchronousQueueTest.modelCheckingTest(SynchronousQueueTest.kt:35)

