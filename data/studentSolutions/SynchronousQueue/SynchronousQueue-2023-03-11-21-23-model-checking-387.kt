import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class Node<T> {
    val cont: AtomicRef<Continuation<Pair<Any, Int>>?> = atomic(null)
    val v: AtomicRef<T?> = atomic(null)
    val next: AtomicRef<Node<T>?> = atomic(null)
    val type: AtomicRef<Int?> = atomic(null)

    constructor(){}

    constructor(type: Int, value: T?) {
        this.type.value = type
        this.v.value = value
    }
}


class SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val firstNode = Node<E>()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    suspend fun send(element: E) {
        while (true) {
            var head = head.value
            val tail = tail.value
            if (head != tail && tail.type.value != TYPE_TASK) {
                head = head
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
            val tail = tail.value
            if (head != tail && tail.type.value != TYPE_RECIEVER) {
                head = head
                var next = head.next.value ?: continue
                if (processDequeue(head, next, null)) {
                    next = next
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
        var curNext = curNext
        if (curNext.cont.value == null) {
            return false
        }

        if (!head.compareAndSet(curHead, curNext)) {
            return false
        }

        if (element != null) {
            curNext.v.value = element
        }
        curNext.cont.value!!.resume(Pair(Any(), STATUS_SUCCESS))
        return true
    }

    suspend fun processEnqueueSender(curTail: Node<E>, type: Int, element: E): Any {
        val newNode = Node(type, element)
        val _tail = tail.value
        if (curTail === _tail) {
            return suspendCoroutine<Any> sc@{ cont ->
                newNode.cont.value = cont
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
        val newNode = Node<E>(type, null)
        val _tail = tail.value
        if (curTail === _tail) {
            return suspendCoroutine<Pair<Any, Int>> sc@{ cont ->
                newNode.cont.value = cont
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
