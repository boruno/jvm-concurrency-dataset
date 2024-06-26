import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object RETRY

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
	private val head: AtomicRef<Node<E>>
	private val tail: AtomicRef<Node<E>>

	init {
		val dummy = Node<E>(null, null)
		head = atomic(dummy)
		tail = atomic(dummy)
	}

	/**
	 * Sends the specified [element] to this channel, suspending if there is no waiting
	 * [receive] invocation on this channel.
	 */
	suspend fun send(element: E) {
		while (true) {
			val h = head.value
			val t = tail.value
			val res = if (t == h || t.isSender()) {
				enqueueAndSuspend(t, element)
			} else {
				dequeueAndResume(h, element)
			}
			if (res === RETRY) continue
			else break
		}
	}

	@Suppress("UNCHECKED_CAST")
	suspend fun receive(): E {
		while (true) {
			val t = tail.value
			val h = head.value
			val res = if (t == h || t.isReceiver()) {
				enqueueAndSuspend(t, null)
			} else {
				dequeueAndResume(h, null)
			}
			if (res === RETRY) continue
			else return res as E
		}
	}

	private suspend fun enqueueAndSuspend(curTail: Node<E>, element: E?): Any? {
		return suspendCoroutine { cont ->
			val newTail = Node(cont, element)
			val retry = !curTail.next.compareAndSet(null, newTail)
			this.tail.compareAndSet(curTail, curTail.next.value!!)

			if (retry) {
				cont.resume(RETRY)
			}
		}
	}

	private fun dequeueAndResume(curHead: Node<E>, element: E?): Any? {
		val newHead = curHead.next.value!!
		return if (this.head.compareAndSet(curHead, newHead)) {
			newHead.cont!!.resume(element)
			newHead.value
		} else {
			RETRY
		}
	}
}

class Node<E>(val cont: Continuation<E?>?, val value: E?) {
	val next = atomic<Node<E>?>(null)
	fun isSender(): Boolean = value != null

	fun isReceiver(): Boolean = !isSender()
}
