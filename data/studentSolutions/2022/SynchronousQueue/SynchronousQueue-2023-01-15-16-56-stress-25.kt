import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.coroutines.*
import kotlin.reflect.typeOf

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val q = FAAQueue<E>()
    private val senders = FAAQueue<Pair<Continuation<Unit>, E>>()
    private val receivers = FAAQueue<Continuation<E>>()

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            if (receivers.isEmpty) {
                suspendCoroutine<Unit> { cont ->
                    senders.enqueue(cont to element)
                }
                return
            } else {
                val r = receivers.dequeue()
                if (r != null) {
                    r.resume(element)
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
            if (senders.isEmpty) {
                return suspendCoroutine { cont ->
                    receivers.enqueue(cont)
                }
            } else {
                val (s, elem) = senders.dequeue()!!
                s.resume(Unit)
                return elem
            }
        }
    }
}

class FAAQueue<E> {
    val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    val enqIdx = atomic(0L)
    val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val currentTail = tail.value
            val i = enqIdx.getAndIncrement()
            val segment = findSegment(currentTail, i / SEGMENT_SIZE)
            moveTailForward(segment)
            if (segment.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) {
                return null
            }
            val currentHead = head.value
            val i = deqIdx.getAndIncrement()
            val segment = findSegment(currentHead, i / SEGMENT_SIZE)
            moveHeadForward(segment)
            if (segment.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, 'T')) {
                continue
            }
            return segment.elements[(i % SEGMENT_SIZE).toInt()].value as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return (deqIdx.value >= enqIdx.value)
        }

    fun findSegment(start: Segment, id: Long): Segment {
        var currentSegment = start
        if (currentSegment.id >= id) {
            return start
        }
        while (true) {
            if (currentSegment.next.value == null) {
                val newSegment = Segment()
                newSegment.id = currentSegment.id + 1L
                if (!currentSegment.next.compareAndSet(null, newSegment)) {
                    continue
                }
                if (newSegment.id == id) {
                    return newSegment
                } else {
                    currentSegment = newSegment
                }
            } else {
                currentSegment = currentSegment.next.value!!
                if (currentSegment.id == id) {
                    return currentSegment
                }
            }
        }
    }

    fun moveTailForward(newTail: Segment) {
        val currentTail = tail.value
        if (newTail.id < currentTail.id) {
            return
        }
        tail.compareAndSet(currentTail, newTail)
    }

    fun moveHeadForward(newHead: Segment) {
        val currentHead = head.value
        if (newHead.id < currentHead.id) {
            return
        }
        head.compareAndSet(currentHead, newHead)
    }
}

class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    var id = 0L

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS