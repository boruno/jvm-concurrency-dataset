import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
	private val q = PriorityQueue<E>()
	private val array = atomicArrayOfNulls<Task<E>?>(32)
	private val lock = atomic(false)

	private fun tryLock() = lock.compareAndSet(expect = false, update = true)

	fun unlock() {
		lock.compareAndSet(expect = true, update = false)
	}

	/**
	 * Retrieves the element with the highest priority
	 * and returns it as the result of this function;
	 * returns `null` if the queue is empty.
	 */
	fun poll(): E? {
		if (tryLock()) {
			val result = q.poll()
			for (i in 0 until 32) {
				when (array[i].value?.status ?: continue) {
					Status.POLL -> array[i].value = Task(Status.DONE, q.poll())
					Status.PEEK -> array[i].value = Task(Status.DONE, q.peek())
					Status.ADD -> {
						q.add(array[i].value!!.value)
						array[i].value = Task(Status.DONE, null)
					}

					Status.DONE -> continue
				}
			}
			unlock()
			return result
		} else {
			var index: Int
			val task: Task<E> = Task(Status.POLL, null)
			while (true) {
				index = ThreadLocalRandom.current().nextInt(32)
				if (array[index].compareAndSet(null, task)) {
					break
				}
			}
			while (!tryLock()) {
				val result = array[index].value ?: continue
				if (result.status == Status.DONE) {
					array[index].value = null
					return result.value
				}
			}
			val result = q.poll()
			array[index].value = null
			for (i in 0 until 32) {
				when (array[i].value?.status ?: continue) {
					Status.POLL -> array[i].value = Task(Status.DONE, q.poll())
					Status.PEEK -> array[i].value = Task(Status.DONE, q.peek())
					Status.ADD -> {
						q.add(array[i].value!!.value)
						array[i].value = Task(Status.DONE, null)
					}

					Status.DONE -> continue
				}
			}
			unlock()
			return result
		}
	}

	/**
	 * Returns the element with the highest priority
	 * or `null` if the queue is empty.
	 */
	fun peek(): E? {
		if (tryLock()) {
			val result = q.peek()
			for (i in 0 until 32) {
				when (array[i].value?.status ?: continue) {
					Status.POLL -> array[i].value = Task(Status.DONE, q.poll())
					Status.PEEK -> array[i].value = Task(Status.DONE, q.peek())
					Status.ADD -> {
						q.add(array[i].value!!.value)
						array[i].value = Task(Status.DONE, null)
					}

					Status.DONE -> continue
				}
			}
			unlock()
			return result
		} else {
			var index: Int
			val task: Task<E> = Task(Status.PEEK, null)
			while (true) {
				index = ThreadLocalRandom.current().nextInt(32)
				if (array[index].compareAndSet(null, task)) {
					break
				}
			}
			while (!tryLock()) {
				val result = array[index].value ?: continue
				if (result.status == Status.DONE) {
					array[index].value = null
					return result.value
				}
			}
			val result = q.peek()
			array[index].value = null
			for (i in 0 until 32) {
				when (array[i].value?.status ?: continue) {
					Status.POLL -> array[i].value = Task(Status.DONE, q.poll())
					Status.PEEK -> array[i].value = Task(Status.DONE, q.peek())
					Status.ADD -> {
						q.add(array[i].value!!.value)
						array[i].value = Task(Status.DONE, null)
					}

					Status.DONE -> continue
				}
			}
			unlock()
			return result
		}
	}

	/**
	 * Adds the specified element to the queue.
	 */
	fun add(element: E) {
		if (tryLock()) {
			q.add(element)
			for (i in 0 until 32) {
				when (array[i].value?.status ?: continue) {
					Status.POLL -> array[i].value = Task(Status.DONE, q.poll())
					Status.PEEK -> array[i].value = Task(Status.DONE, q.peek())
					Status.ADD -> {
						q.add(array[i].value!!.value)
						array[i].value = Task(Status.DONE, null)
					}

					Status.DONE -> continue
				}
			}
			unlock()
		} else {
			var index: Int
			val task: Task<E> = Task(Status.ADD, null)
			while (true) {
				index = ThreadLocalRandom.current().nextInt(32)
				if (array[index].compareAndSet(null, task)) {
					break
				}
			}
			while (!tryLock()) {
				val result = array[index].value ?: continue
				if (result.status == Status.DONE) {
					array[index].value = null
					q.add(element)
				}
			}
			q.add(element)
			array[index].value = null
			for (i in 0 until 32) {
				when (array[i].value?.status ?: continue) {
					Status.POLL -> array[i].value = Task(Status.DONE, q.poll())
					Status.PEEK -> array[i].value = Task(Status.DONE, q.peek())
					Status.ADD -> {
						q.add(array[i].value!!.value)
						array[i].value = Task(Status.DONE, null)
					}

					Status.DONE -> continue
				}
			}
			unlock()
		}
	}
}

enum class Status {
	POLL, PEEK, ADD, DONE
}

class Task<E>(val status: Status, val value: E?)