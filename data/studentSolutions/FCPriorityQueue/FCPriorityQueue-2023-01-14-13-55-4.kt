import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
	private val q = PriorityQueue<E>()
	private val array = atomicArrayOfNulls<Task<E>?>(4)
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
		var index: Int
		val task: Task<E> = Task(Status.POLL, null)
		while (true) {
			index = ThreadLocalRandom.current().nextInt(4)
			if (array[index].compareAndSet(null, task)) {
				break
			}
		}
		while (true) {
			if (tryLock()) {
				for (i in 0 until 4) {
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
				val res = array[index].getAndSet(null) ?: continue
				return res.value
			} else {
				if (array[index].value?.status == Status.DONE) {
					val res = array[index].getAndSet(null) ?: continue
					return res.value
				}
			}
		}
	}

	/**
	 * Returns the element with the highest priority
	 * or `null` if the queue is empty.
	 */
	fun peek(): E? {
		var index: Int
		val task: Task<E> = Task(Status.PEEK, null)
		while (true) {
			index = ThreadLocalRandom.current().nextInt(4)
			if (array[index].compareAndSet(null, task)) {
				break
			}
		}
		while (true) {
			if (tryLock()) {
				for (i in 0 until 4) {
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
				val res = array[index].getAndSet(null) ?: continue
				return res.value
			} else {
				if (array[index].value?.status == Status.DONE) {
					val res = array[index].getAndSet(null) ?: continue
					return res.value
				}
			}
		}
	}

	/**
	 * Adds the specified element to the queue.
	 */
	fun add(element: E) {
		var index: Int
		val task: Task<E> = Task(Status.ADD, null)
		while (true) {
			index = ThreadLocalRandom.current().nextInt(4)
			if (array[index].compareAndSet(null, task)) {
				break
			}
		}
		while (true) {
			if (tryLock()) {
				for (i in 0 until 4) {
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
				array[index].getAndSet(null) ?: continue
				break
			} else {
				if (array[index].value?.status == Status.DONE) {
					array[index].getAndSet(null) ?: continue
					break
				}
			}
		}
	}
}

enum class Status {
	POLL, PEEK, ADD, DONE
}

class Task<E>(val status: Status, val value: E?)