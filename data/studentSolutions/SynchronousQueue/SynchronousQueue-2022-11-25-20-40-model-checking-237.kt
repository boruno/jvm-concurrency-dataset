import kotlinx.atomicfu.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val sendIdx = atomic(0L)
    private val receiveIdx = atomic(0L)
    private val lastNode = Segment(0)


    private fun findSegment(index: Long): Segment {
        var curNode = lastNode
        while (curNode.index < index) {
            if (curNode.next.value != null) {
                curNode = curNode.next.value!!
            } else {
                if (curNode.next.compareAndSet(null, Segment(curNode.index + 1)))
                    continue
                curNode = curNode.next.value!!
            }
        }
        return curNode
    }




    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val index = sendIdx.getAndAdd(1)
            val segment = findSegment(index / SEGMENT_SIZE)
            val indexInSegment = (index % SEGMENT_SIZE).toInt()
            if (index < receiveIdx.value) {
                if (segment.cas(indexInSegment, null, InvalidCell()))
                    continue
                val cell = segment.get(indexInSegment)!!
                val (coroutine, _) = cell as Pair<Continuation<E?>, Any>
                coroutine.resume(element)
            } else {
                val res = suspendCoroutine<E?> sc@{ cont ->
                    if (!segment.cas(indexInSegment, null, Pair(cont, element))) {
                        cont.resume(null)
                        return@sc
                    }
                }
                if (res == null) continue
            }
            return
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while (true) {
            val index = receiveIdx.getAndAdd(1)
            val segment = findSegment(index / SEGMENT_SIZE)
            val indexInSegment = (index % SEGMENT_SIZE).toInt()
            if (index < sendIdx.value) {
                if (segment.cas(indexInSegment, null, InvalidCell()))
                    continue
                val cell = segment.get(indexInSegment)!!
                if (cell is InvalidCell)
                    continue
                val (coroutine, value) = cell as Pair<Continuation<E?>, E>
                coroutine.resume(value)
                return value
            } else {
                val res = suspendCoroutine<E?> sc@{ cont ->
                    if (!segment.cas(indexInSegment, null, Pair(cont, null))) {
                        cont.resume(null)
                        return@sc
                    }
                }
                if (res == null) continue
                return res
            }
        }
    }

    private class InvalidCell

}

private class Segment(val index: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any) =
        elements[i].compareAndSet(expect, update)
}

const val SEGMENT_SIZE: Int = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

