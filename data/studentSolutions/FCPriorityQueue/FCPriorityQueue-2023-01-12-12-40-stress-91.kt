import Operation.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
  private val q = PriorityQueue<E>()
  private val ARRAY_SIZE = 42
  private val array = atomicArrayOfNulls<Operation<E>>(ARRAY_SIZE)
  private val random = Random()
  private val lock = atomic(false)

  private fun addOperation(operation: Operation<E>): E? {
    while (true) {
      val idx = random.nextInt(ARRAY_SIZE)
      if (array[idx].compareAndSet(null, operation)) {
        if (lock.compareAndSet(expect = false, update = true)) {
          for (i in 0 until ARRAY_SIZE) {
            when (val currentOp = array[i].value) {
              is Add -> array[i].value = DONE(null as E?).also { q.add(currentOp.value) }
              is Peek -> array[i].value = DONE(q.peek())
              is Poll -> array[i].value = DONE(q.poll())
              else -> {}
            }
          }
          lock.value = false
        }
        val currentOp = array[idx].value
        if (currentOp is DONE) {
          array[idx].value = null
          return currentOp.value
        }
      }
    }
  }

  /**
   * Retrieves the element with the highest priority
   * and returns it as the result of this function;
   * returns `null` if the queue is empty.
   */
  fun poll(): E? {
    return addOperation(Poll())
  }

  /**
   * Returns the element with the highest priority
   * or `null` if the queue is empty.
   */
  fun peek(): E? {
    return addOperation(Peek())
  }

  /**
   * Adds the specified element to the queue.
   */
  fun add(element: E) {
    addOperation(Add(element))
  }
}

sealed class Operation<E> {
  class Poll<E> : Operation<E>()
  class Peek<E> : Operation<E>()
  class Add<E>(val value: E) : Operation<E>()
  class DONE<E>(val value: E?) : Operation<E>()
}