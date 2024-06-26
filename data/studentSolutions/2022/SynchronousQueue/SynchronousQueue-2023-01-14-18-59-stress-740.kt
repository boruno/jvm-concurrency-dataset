import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SynchronousQueue<E> {
	private object RETRY


	private val head: AtomicRef<Node<E>>
	private val tail: AtomicRef<Node<E>>

	init {
		val dummy = Node<E>(null, null)
		head = atomic(dummy)
		tail = atomic(dummy)
	}

	suspend fun send(element: E) {
		while (true) {
			val t = tail.value
			val h = head.value
			val res = if (t == h || t.isSender()) {
				enqueueAndSuspend(t, element)
			} else {
				dequeueAndResume(h, element)
			}

			if (res != RETRY) {
				break
			}
		}
	}

	suspend fun receive(): E {
		while (true) {
			val head = head.value
			val tail = tail.value

			val res = if (head == tail || tail.value == null) {
				enqueueAndSuspend(tail, null)
			} else {
				dequeueAndResume(head, null)
			}

			if (res != RETRY) {
				return res as E
			}
		}
	}

	private suspend fun enqueueAndSuspend(tail: Node<E>, element: E?): Any? {
		return suspendCoroutine { cont ->
			val newTail = Node(cont, element)
			val retry = !tail.next.compareAndSet(null, newTail)
			this.tail.compareAndSet(tail, tail.next.value!!)

			if (retry) {
				cont.resume(RETRY)
			}
		}
	}

	private fun dequeueAndResume(head: Node<E>, element: E?): Any? {
		val newHead = head.next.value!!
		return if (this.head.compareAndSet(head, newHead)) {
			newHead.cont!!.resume(element)
			newHead.value
		} else {
			RETRY
		}
	}
}

class Node<E>(val cont: Continuation<Any?>?, val value: E?) {
	val next = atomic<Node<E>?>(null)
	fun isSender(): Boolean = value != null

	fun isReceiver(): Boolean = !isSender()
}