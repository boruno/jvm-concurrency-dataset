import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    val recieveElement = ReceiveElement<E>()
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        while (true) {
            val curSendSeg = sendSeg.value
            val i = sendIdx.getAndIncrement()
            val s = findSegment(curSendSeg, i / SEGMENT_SIZE)
            if (s.id > sendSeg.value.id) {
                sendSeg.getAndSet(s)
            }

            if (i < receiveIdx.value) {
                if (s.cas((i % SEGMENT_SIZE).toInt(), recieveElement, RendezvousElement(element))) return else continue
            } else {
                if (s.cas((i % SEGMENT_SIZE).toInt(), null, SendElement(element))) {
                    while (true) {
                        if (s.get((i % SEGMENT_SIZE).toInt()) is RendezvousElement) {
                            return
                        }
                    }
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
            val curReceiveSeg = receiveSeg.value
            val i = receiveIdx.getAndIncrement()
            val s = findSegment(curReceiveSeg, i / SEGMENT_SIZE)
            if (s.id > receiveSeg.value.id) {
                receiveSeg.getAndSet(s)
            }

            if (i < sendIdx.value) {
                val oldElement = s.get((i % SEGMENT_SIZE).toInt())
                if (oldElement is SendElement) {
                    if (s.cas((i % SEGMENT_SIZE).toInt(), oldElement, RendezvousElement(null))) return oldElement.value!! else continue
                }
            } else {
                if (s.cas((i % SEGMENT_SIZE).toInt(), null, ReceiveElement())) {
                    while (true) {
                        val newValue = s.get((i % SEGMENT_SIZE).toInt())
                        if (newValue is RendezvousElement) {
                            return newValue.value!!
                        }
                    }
                }
            }
        }
    }


    private val receiveSeg: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val sendSeg: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val sendIdx = atomic(0L)
    private val receiveIdx = atomic(0L)

    init {
        val firstNode = Segment<E>()
        receiveSeg = atomic(firstNode)
        sendSeg = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    /*fun enqueue(element: E) {
        while(true) {
            val curTail = sendSeg.value
            val i = sendIdx.getAndIncrement()
            val s = findSegment(curTail, i / SEGMENT_SIZE)
            if (s.id > sendSeg.value.id) {
                sendSeg.getAndSet(s)
            }
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, SegmentElement(element))) return
        }
    }*/

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    /*fun dequeue(): E? {
        while(true) {
            if (receiveIdx.value >= sendIdx.value) return null
            val curHead = receiveSeg.value
            val i = receiveIdx.getAndIncrement()
            val s = findSegment(curHead, i / SEGMENT_SIZE)
            if (s.id > receiveSeg.value.id) {
                receiveSeg.getAndSet(s)
            }
            val segInd = (i % SEGMENT_SIZE).toInt()
            if (s.cas(segInd, null, SegmentElement(null))) continue
            return s.get(segInd)!!.value
        }
    }*/

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return receiveIdx.value >= sendIdx.value
        }
}

private class Segment<E> (val id : Long = 0L) {
    val next: AtomicRef<Segment<E>?> = atomic(null)
    val elements = atomicArrayOfNulls<Element<E>>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Element<E>?, update: Element<E>?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Element<E>?) {
        elements[i].value = value
    }

    fun findNext() :Segment<E> {
        next.compareAndSet(null, Segment(id+1))
        return next.value!!
    }
}

open class Element<E>(val value: E? = null)
class SendElement<E> (value: E) : Element<E>(value)
class ReceiveElement<E> () : Element<E>(null)
class RendezvousElement<E> (value: E? = null) : Element<E>(value)

private fun <E> findSegment(start : Segment<E>, id : Long) : Segment<E> {
    var seg = start
    while (seg.id <= id) {
        seg = seg.findNext()
    }
    return seg
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

