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
    private val head: AtomicRef<Segment>
    private val tail: AtomicRef<Segment>
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0L)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val curTail = tail
            val s = enqIdx.getAndIncrement()
            var curSegment = curTail.value
            while (curSegment.id < s / SEGMENT_SIZE) {
                val next = curSegment.next.value
                if (next == null) {
                    curSegment.next.compareAndSet(null, Segment(curSegment.id + 1))
                }
                curSegment = curSegment.next.value!!
            }
            val receiveIdx = deqIdx.value
            if (s < receiveIdx) {
                if (curSegment.cas((s % SEGMENT_SIZE).toInt(), null, Node(element))) {
                    return
                }
                val node = curSegment.get((s % SEGMENT_SIZE).toInt()) as? Node<E> ?: continue
                val value = node.element.value
                node.element.compareAndSet(value, element)
                node.cor.value!!.resume(false)
                return
            } else {
                if (!sus(curSegment, Node(element), s)) {
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
            val curHead = head.value
            val r = deqIdx.getAndIncrement()
            var currentSegment = curHead
            while (currentSegment.id < r / SEGMENT_SIZE) {
                val next = currentSegment.next.value
                if (next == null) {
                    currentSegment.next.compareAndSet(null, Segment(currentSegment.id + 1))
                }
                currentSegment = currentSegment.next.value!!
            }
            head.compareAndSet(curHead, currentSegment)
            val sendIdx = enqIdx.value
            if (r < sendIdx) {
                if (currentSegment.cas((r % SEGMENT_SIZE).toInt(), null, Any())) {
                    continue
                }
                val cell = currentSegment.get((r % SEGMENT_SIZE).toInt()) as Node<E>
                cell.cor.value?.resume(false)
                return cell.element.value!!
            } else {
                if (!sus(currentSegment, Node(null), r))
                    return (currentSegment.get((r % SEGMENT_SIZE).toInt()) as Node<E>).element.value!!
            }
        }
    }

    private suspend fun sus(currentSegment: Segment, node: Node<E>, r: Long): Boolean {
        return suspendCoroutine sc@{ cor ->
            node.cor.value = cor
            if (!currentSegment.cas((r % SEGMENT_SIZE).toInt(), null, node)) {
                cor.resume(true)
                return@sc
            }
        }
    }
}

private class Node<E>(el: E?) {
    val element: AtomicRef<E?> = atomic(el)
    val cor: AtomicRef<Continuation<Boolean>?> = atomic(null)
}

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2