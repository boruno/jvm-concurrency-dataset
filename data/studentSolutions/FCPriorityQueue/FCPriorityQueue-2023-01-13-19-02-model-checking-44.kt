import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.PriorityQueue
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
	private val q = PriorityQueue<E>()
	private val array = atomicArrayOfNulls<Task?>(8)
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
		return processTask(PollTask())
	}

	/**
	 * Returns the element with the highest priority
	 * or `null` if the queue is empty.
	 */
	fun peek(): E? {
		return processTask(PeekTask())
	}

	/**
	 * Adds the specified element to the queue.
	 */
	fun add(element: E) {
		processTask(AddTask(element))
	}

	private fun processTask(task : Task): E? {
		var i = ThreadLocalRandom.current().nextInt(0, 8)
		while (true) {
			if (array[i].compareAndSet(null, task)) {
				break
			}
			i = (i + 1) % 8
		}
		while (true) {
			if (tryLock()) {
				for (j in 0 until 8) {
					val curTask = array[j].getAndSet(null) // only this thread works here, don't need CAS
					if (curTask != null) {
						curTask.process()
						curTask.ready.getAndSet(true)
					}
				}
				unlock()
				break
			} else if (task.ready.value) {
				break
			}
		}
		return task.result
	}

	abstract inner class Task {
		abstract fun process()
		var result: E? = null
		val ready: AtomicBoolean = atomic(false)
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
	inner class AddTask(private val element : E) : Task() {
		override fun process() {
			q.add(element)
		}
	}
}