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
    private val head: AtomicRef<Node<Coroutine<E>>>
    private val tail: AtomicRef<Node<Coroutine<E>>>

    init {
        val dummy = Node<Coroutine<E>>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    fun tryEnqueue(x: Coroutine<E>): OperationResult {
        val node = Node(x);
        val cur_tail = this.tail.value;

        if (cur_tail.x != null) {
            if (cur_tail.x.coroutineType == x.coroutineType) {
                if (cur_tail.next.compareAndSet(null, node)) {
                    tail.compareAndSet(cur_tail, node);
                    return OperationResult.SUCCESS;
                }
                else {
                    val cur_tail_next = cur_tail.next.value
                    if (cur_tail_next != null) {
                        tail.compareAndSet(cur_tail, cur_tail_next)
                    }
                    return OperationResult.FAILURE
                }
            }
            else {
                return OperationResult.FAILURE
            }
        }
        else {
            if (cur_tail.next.compareAndSet(null, node)) {
                tail.compareAndSet(cur_tail, node);
                return OperationResult.SUCCESS;
            }
            else {
                val cur_tail_next = cur_tail.next.value
                if (cur_tail_next != null) {
                    tail.compareAndSet(cur_tail, cur_tail_next)
                }
                return OperationResult.FAILURE
            }
        }
    }

    fun dequeue(coroutineType: CoroutineType): Pair<Coroutine<E>?, OperationResult> {
        while (true) {
            val cur_head = this.head.value;
            val cur_head_next = cur_head.next.value;
            if (cur_head_next == null) {
                return Pair(null, OperationResult.SUCCESS)
            }
            if (cur_head_next.x?.coroutineType == coroutineType) {
                if (this.head.compareAndSet(cur_head, cur_head_next)) {
                    return Pair(cur_head_next.x, OperationResult.SUCCESS);
                }
            }
            else {
                return Pair(null, OperationResult.FAILURE)
            }
        }
    }

    fun peek(): Coroutine<E>? {
        return head.value.next.value?.x
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            var result = OperationResult.SUCCESS

            val (curCor, dequeueResult) = dequeue(CoroutineType.RECEIVER)
            if (dequeueResult == OperationResult.SUCCESS) {
                if (curCor != null) {
                    curCor.receiver?.resume(Pair(element, OperationResult.SUCCESS))
                    return
                }
                else {
                    result = suspendCoroutine<OperationResult> sc@ { cont -> run {
                        val res = tryEnqueue(Coroutine(CoroutineType.SENDER, cont to element))
                        if (res == OperationResult.FAILURE) {
                            cont.resume(OperationResult.FAILURE)
                            return@sc
                        }
                    } }
                }
            }
            else {
                result = suspendCoroutine<OperationResult> sc@{ cont ->
                    run {
                        val res = tryEnqueue(Coroutine(CoroutineType.SENDER, cont to element))
                        if (res == OperationResult.FAILURE) {
                            cont.resume(OperationResult.FAILURE)
                            return@sc
                        }
                    }
                }
            }

            if (result == OperationResult.SUCCESS) {
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
            var result = Pair<E?, OperationResult>(null, OperationResult.FAILURE)

            val (curCor, dequeueResult) = dequeue(CoroutineType.SENDER)
            if (dequeueResult == OperationResult.SUCCESS) {
                if (curCor != null) {
                    curCor.sender?.first?.resume(OperationResult.SUCCESS)
                    return curCor.sender?.second!!
                }
                else {
                    result = suspendCoroutine<Pair<E?, OperationResult>> sc@{ cont ->
                        run {
                            val res = tryEnqueue(Coroutine(CoroutineType.SENDER, cont))
                            if (res == OperationResult.FAILURE) {
                                cont.resume(Pair(null, OperationResult.FAILURE))
                                return@sc
                            }
                        }
                    }
                }
            }
            else {
                result = suspendCoroutine<Pair<E?, OperationResult>> sc@{ cont ->
                    run {
                        val res = tryEnqueue(Coroutine(CoroutineType.SENDER, cont))
                        if (res == OperationResult.FAILURE) {
                            cont.resume(Pair(null, OperationResult.FAILURE))
                            return@sc
                        }
                    }
                }
            }

            if (result.second == OperationResult.SUCCESS) {
                return result.first!!
            }
        }
    }
}

enum class CoroutineType {
    SENDER,
    RECEIVER
}

enum class OperationResult {
    SUCCESS,
    FAILURE
}

class Coroutine<E>() {
    lateinit var coroutineType: CoroutineType;
    var sender: Pair<Continuation<OperationResult>, E>? = null
    var receiver: Continuation<Pair<E?, OperationResult>>? = null

    constructor(coroutineType: CoroutineType, sender: Pair<Continuation<OperationResult>, E>) : this() {
        this.coroutineType = coroutineType
        this.sender = sender
    }

    constructor(coroutineType: CoroutineType, receiver: Continuation<Pair<E?, OperationResult>>) : this() {
        this.coroutineType = coroutineType
        this.receiver = receiver
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}