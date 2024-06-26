import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class Node<T> {
    val continuation: AtomicRef<Continuation<Pair<Any, Int>>?> = atomic(null)
    val v: AtomicRef<T?> = atomic(null)
    val next: AtomicRef<Node<T>?> = atomic(null)
    val type: AtomicRef<Int?> = atomic(null)

    constructor(type: Int, value: T?) {
        this.type.value = type
        this.v.value = value
    }
}


class SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val firstNode = Node<E>(TYPE_UNDEFINED, null)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    suspend fun send(element: E) {
        while (true) {
            var head = head.value
            val tail = tail.value
            if (head != tail && tail.type.value != TYPE_TASK) {
                val next = head.next.value ?: continue
                if (processDequeue(head, next, element)) {
                    return
                }
            } else {
                val result = processEnqueueSender(tail, TYPE_TASK, element)
                if (result.second == STATUS_FAIL) {
                    continue
                } else {
                    return
                }
            }
        }
    }

    suspend fun receive(): E {
        while (true) {
            var head = head.value
            val tail = tail.value
            if (head != tail && tail.type.value != TYPE_RECIEVER) {
                head = head
                var next = head.next.value ?: continue
                if (processDequeue(head, next, null)) {
                    next = next
                    return next.v.value!!
                }
            } else {
                val newNode = Node<E>(TYPE_RECIEVER, null)
                val result = processEnqueueReciever(tail, TYPE_RECIEVER, newNode)
                if (result.second == STATUS_FAIL) {
                    continue
                } else {
                    return newNode.v.value as E
                }
            }
        }
    }

    suspend fun processDequeue(curHead: Node<E>, curNext: Node<E>, element: E?): Boolean {
        if (curNext.continuation.value == null) {
            return false
        }

        if (!head.compareAndSet(curHead, curNext)) {
            return false
        }

        if (element != null) {
            curNext.v.value = element
        }
        curNext.continuation.value!!.resume(Pair(Any(), STATUS_SUCCESS))
        return true
    }

    suspend fun processEnqueueSender(curTail: Node<E>, type: Int, element: E): Pair<Any, Int> {
        val newNode = Node(type, element)
        return suspendCoroutine<Pair<Any, Int>> sc@{ continuation ->
            newNode.continuation.value = continuation
            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
                continuation.resume(Pair(Any(), STATUS_FAIL))
                return@sc
            }
        }
    }

    suspend fun processEnqueueReciever(curTail: Node<E>, type: Int, newNode: Node<E>): Pair<Any, Int> {
        return suspendCoroutine<Pair<Any, Int>> sc@{ continuation ->
            newNode.continuation.value = continuation
            if (curTail.next.compareAndSet(null, newNode as Node<E>)) {
                tail.compareAndSet(curTail, newNode)
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
                continuation.resume(Pair(newNode.v.value!!, STATUS_FAIL))
                return@sc
            }
        }
    }
}


val TYPE_UNDEFINED = -1
val TYPE_TASK = 0
val TYPE_RECIEVER = 1

val STATUS_FAIL = 0
val STATUS_SUCCESS = 1
