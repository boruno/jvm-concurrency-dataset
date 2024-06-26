import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


sealed class Node<T> {
}

class UndefinedNode<T>: Node<T> {
    val type: AtomicRef<Int?> = atomic(null)
    val next: AtomicRef<Node<T>?> = atomic(null)
    val cont: AtomicRef<Continuation<Any>?> = atomic(null)
    val v: AtomicRef<T?> = atomic(null)
    constructor() {
    }
}

class NodeSender<T>: Node<T> {
    val type: AtomicRef<Int?> = atomic(null)
    val next: AtomicRef<Node<T>?> = atomic(null)
    val cont: AtomicRef<Continuation<Any>?> = atomic(null)
    val v: AtomicRef<T?> = atomic(null)

    constructor(type: Int, value: T?) {
        this.type.value = type
        this.v.value = value
    }
}

class NodeReciever<T>: Node<T> {
    val type: AtomicRef<Int?> = atomic(null)
    val next: AtomicRef<Node<T>?> = atomic(null)
    val cont: AtomicRef<Continuation<Pair<Any, Int>>?> = atomic(null)
    val v: AtomicRef<T?> = atomic(null)

    constructor(type: Int, value: T?) {
        this.type.value = type
        this.v.value = value
    }
}


class SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val firstNode = UndefinedNode<E>()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    suspend fun send(element: E) {
        while (true) {
            var head = head.value
            val tail = tail.value as UndefinedNode<E>
            if (head != tail && tail.type.value != TYPE_TASK) {
                head = head as NodeSender<E>
                val next = head.next.value ?: continue
                if (processDequeue(head, next, element)) {
                    return
                }
            } else {
                val result = processEnqueueSender(tail, TYPE_TASK, element)
                if (result == STATUS_FAIL) {
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
            val tail = tail.value as UndefinedNode<E>
            if (head != tail && tail.type.value != TYPE_RECIEVER) {
                head = head as NodeReciever<E>
                var next = head.next.value ?: continue
                if (processDequeue(head, next, null)) {
                    next = next as NodeSender<E>
                    return next.v.value!!
                }
            } else {
                val result = processEnqueueReciever(tail, TYPE_RECIEVER)
                if (result.second == STATUS_FAIL) {
                    continue
                } else {
                    return result.first as E
                }
            }
        }
    }

    suspend fun processDequeue(curHead: Node<E>, curNext: Node<E>, element: E?): Boolean {
        var curNext = curNext as NodeSender<E>
        if (curNext.cont.value == null) {
            return false
        }

        if (!head.compareAndSet(curHead, curNext)) {
            return false
        }

        if (element != null) {
            curNext.v.value = element
        }
        curNext.cont.value!!.resume(STATUS_SUCCESS)
        return true
    }

    suspend fun processEnqueueSender(curTail: Node<E>, type: Int, element: E): Any {
        val newNode = NodeSender(type, element)
        val _tail = tail.value
        if (curTail === _tail) {
            return suspendCoroutine<Any> sc@{ cont ->
                newNode.cont.value = cont
                var curTail = curTail as NodeSender<E>
                if (curTail.next.compareAndSet(null, newNode)) {
                    tail.compareAndSet(curTail, newNode)
                } else {
                    tail.compareAndSet(curTail, curTail.next.value!!)
                    cont.resume(STATUS_FAIL)
                    return@sc
                }
            }
        }
        return STATUS_FAIL
    }

    suspend fun processEnqueueReciever(curTail: Node<E>, type: Int): Pair<Any, Int> {
        val newNode = NodeReciever<E>(type, null)
        val _tail = tail.value
        if (curTail === _tail) {
            return suspendCoroutine<Pair<Any, Int>> sc@{ cont ->
                newNode.cont.value = cont
                var curTail = curTail as NodeReciever<E>
                if (curTail.next.compareAndSet(null, newNode)) {
                    tail.compareAndSet(curTail, newNode)
                } else {
                    tail.compareAndSet(curTail, curTail.next.value!!)
                    cont.resume(Pair(Any(), STATUS_FAIL))
                    return@sc
                }
            }
        }
        return Pair(Any(), STATUS_FAIL)
    }
}


val TYPE_UNDEFINED = -1
val TYPE_TASK = 0
val TYPE_RECIEVER = 1

val STATUS_FAIL = 0
val STATUS_SUCCESS = 1
