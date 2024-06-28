import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val sendIdx = atomic(0L)
    private val receiveIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        tail = atomic(firstNode)
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val currentTail = tail.value
        val index = sendIdx.getAndIncrement()
        val segment = findSegment(currentTail, index / SEGMENT_SIZE)
        moveTailForward(segment)
        suspendCoroutine { cont ->
            if (!segment.cas(index.toInt() % SEGMENT_SIZE, null, cont to element)) {
                val receiveCont = segment.get(index.toInt() % SEGMENT_SIZE) as Continuation<E>
                receiveCont.resume(element)
                cont.resume(Unit)
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val currentTail = tail.value
        val index = receiveIdx.getAndIncrement()
        val segment = findSegment(currentTail, index / SEGMENT_SIZE)
        moveTailForward(segment)
        return suspendCoroutine { cont ->
            if (!segment.cas(index.toInt() % SEGMENT_SIZE, null, cont)) {
                val (sendCont, element) = segment.get(index.toInt() % SEGMENT_SIZE) as Pair<Continuation<Unit>, E>
                sendCont.resume(Unit)
                cont.resume(element)
            }
        }
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var currentSegment = start
        while (currentSegment.id != id) {
            currentSegment = currentSegment.next
        }
        return currentSegment
    }

    private fun moveTailForward(segment: Segment) {
        while (true) {
            val currentTail = tail.value
            if (currentTail.id >= segment.id) {
                return
            }
            tail.compareAndSet(currentTail, segment)
        }
    }
}

private class Segment(val id: Long) {
    private val _next: AtomicRef<Segment?> = atomic(null)
    val next: Segment
        get() {
            _next.value?.let { return it }
            val newSegment = Segment(id + 1)
            _next.compareAndSet(null, newSegment)
            return _next.value!!
        }
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
