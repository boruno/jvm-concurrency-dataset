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

    private val head: AtomicRef<Segment<E>>
    private val tail: AtomicRef<Segment<E>>
    private val sendIdx = atomic(0L)
    private val receiveIdx = atomic(0L)

    init {
        val firstNode = Segment<E>()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }


    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while(true) {
            val curTail = tail.value
            val curSendIdx = sendIdx.getAndIncrement() // <--- FAA(sendIdx, +1)
            val segment = findSegment(curTail, curSendIdx)

            moveTail(segment)

            val idx = getSegmentIndex(curSendIdx)

            if(curSendIdx <= receiveIdx.value) {
                val node = segment.get(idx)
                if(node == null) {
                    if(segment.get(idx) is KILL) continue
                    val result = suspendCoroutine sc@ { cont ->
                        if (!segment.cas(idx,null, Node(element, cont))) {
                            cont.resume(RETRY)
                            return@sc
                        }
                    }
                    if (result != RETRY) return
                }
                (node as Node<E>).cont.resume(element!!)
                return
            } else {
                if(segment.get(idx) is KILL) continue
                val result = suspendCoroutine sc@ { cont ->
                    if (!segment.cas(idx,null, Node(element, cont))) {
                        cont.resume(RETRY)
                        return@sc
                    }
                }
                if (result != RETRY) return
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        while(true) {
            //if (isEmpty) return null
            val curHead = head.value
            val curReceiveIdx = receiveIdx.getAndIncrement() // <--- FAA(receiveIdx, +1)
            val segment = findSegment(curHead, curReceiveIdx)

            moveHead(segment)

            val idx = getSegmentIndex(curReceiveIdx)

            if(curReceiveIdx <= sendIdx.value) {
                if (segment.cas(idx, null, KILL()))
                    continue
                val node = segment.get(idx) as Node<E>
                node.cont.resume(Unit)
                return node.element!!
            } else {
                val result = suspendCoroutine sc@ { cont ->
                    if (!segment.cas(idx,null, Node(null, cont))) {
                        cont.resume(RETRY)
                        return@sc
                    }
                }
                if (result != RETRY) return result as E
            }
        }
    }

    private fun moveTail(segment: Segment<E>) {
        while(true) {
            val curTail = tail.value
            if(segment.id <= curTail.id) break
            if(tail.compareAndSet(curTail, segment)) break
        }
    }

    private fun moveHead(segment: Segment<E>) {
        while(true) {
            val curHead = head.value
            if(segment.id <= curHead.id) break
            if(head.compareAndSet(curHead, segment)) break
        }
    }

    private val isEmpty: Boolean
        get() {
            return receiveIdx.value >= sendIdx.value
        }

    private fun findSegment(s: Segment<E>, index: Long): Segment<E> {
        val id = index / SEGMENT_SIZE

        var segment = s

        while(segment.id != id)
            segment = segment.getOrCreateNext()

        return segment
    }

    private fun getSegmentIndex(i: Long): Int {
        return (i % SEGMENT_SIZE).toInt()
    }
}

private class Node<E>(val element: E?, val cont: Continuation<Any>)

private class Segment<E>(val id: Long = 0L) {
    val next = atomic<Segment<E>?>(null)

    val elements = atomicArrayOfNulls<Any?>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)

    fun getOrCreateNext(): Segment<E> {
        next.compareAndSet(null, Segment(id+1))
        return next.value!!
    }
}

private class KILL // dummy class to fill empty elements in dequeue method (┴)
private class Retry
private val RETRY = Retry()

private const val SEGMENT_SIZE = 2