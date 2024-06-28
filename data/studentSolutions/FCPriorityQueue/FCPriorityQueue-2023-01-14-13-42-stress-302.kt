import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.PriorityQueue
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
	private val q = PriorityQueue<E>()
	private val array = atomicArrayOfNulls<Task?>(4)
	private val lock = atomic(false)

	private fun tryLock() = lock.compareAndSet(expect = false, update = true)

	private fun unlock() {
		lock.compareAndSet(expect = true, update = false)
	}

	/**
	 * Retrieves the element with the highest priority
	 * and returns it as the result of this function;
	 * returns `null` if the queue is empty.
	 */
	fun poll(): E? {
		val task = PollTask()
		var index: Int
		while (true) {
			index = ThreadLocalRandom.current().nextInt(4)
			if (array[index].compareAndSet(null, task)) {
				break
			}
		}
		while (true) {
			if (tryLock()) {
				for (j in 0 until 4) {
					val curTask = array[j].getAndSet(null)
					if (curTask != null) {
						curTask.process()
						curTask.ready = true
					}
				}
				unlock()
				return task.result
			} else if (task.ready) {
				return task.result
			}
		}
	}

	/**
	 * Returns the element with the highest priority
	 * or `null` if the queue is empty.
	 */
	fun peek(): E? {
		val task = PeekTask()
		var index: Int
		while (true) {
			index = ThreadLocalRandom.current().nextInt(4)
			if (array[index].compareAndSet(null, task)) {
				break
			}
		}
		while (true) {
			if (tryLock()) {
				for (j in 0 until 4) {
					val curTask = array[j].getAndSet(null)
					if (curTask != null) {
						curTask.process()
						curTask.ready = true
					}
				}
				unlock()
				return task.result
			} else if (task.ready) {
				return task.result
			}
		}
	}

	/**
	 * Adds the specified element to the queue.
	 */
	fun add(element: E) {
		val task = AddTask(element)
		var index: Int
		while (true) {
			index = ThreadLocalRandom.current().nextInt(4)
			if (array[index].compareAndSet(null, task)) {
				break
			}
		}
		while (true) {
			if (tryLock()) {
				val curTask = array[index].getAndSet(null)
				if (curTask != null) {
					q.add(element)
				}
				for (j in 0 until 4) {
					val curTask = array[j].getAndSet(null)
					if (curTask != null) {
						curTask.process()
						curTask.ready = true
					}
				}
				unlock()
				break
			} else if (task.ready) {
				break
			}
		}
	}


	abstract inner class Task {
		abstract fun process()
		var result: E? = null
		var ready = false
	}

	inner class PollTask : Task() {
		override fun process() {
			result = q.poll()
		}
	}

	inner class PeekTask : Task() {
		override fun process() {
			result = q.peek()
		}
	}

	inner class AddTask(private val element: E) : Task() {
		override fun process() {
			q.add(element)
		}
	}
}