package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class TreiberStackWithElimination<E> {
  private val top = atomic<Node<E>?>(null)
  private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)
  private val random = Random()

  /**
   * Adds the specified element [x] to the stack.
   */
  fun push(x: E) {
//    if (eliminationArray.putRandom(ELIMINATION_ARRAY_SIZE, x, REPEATS)) return

    repeat(REPEATS) {
      val index = random.nextInt(ELIMINATION_ARRAY_SIZE)
      if (eliminationArray[index].compareAndSet(null, x)) return
    }

    do {
      val oldTopNode = top.value
      val newTopNode = Node(x, oldTopNode)
    } while (!top.compareAndSet(oldTopNode, newTopNode))
  }

  /**
   * Retrieves the first element from the stack
   * and returns it; returns `null` if the stack
   * is empty.
   */
  fun pop(): E? {
//    eliminationArray.popRandom(ELIMINATION_ARRAY_SIZE, REPEATS)?.let { return it }

    for (i in 0 until REPEATS) {
      val index = random.nextInt(ELIMINATION_ARRAY_SIZE)
      return eliminationArray[index].getAndSet(null) ?: continue
    }

    while (true) {
      top.value?.let {
        if (top.compareAndSet(it, it.next)) return it.x
      } ?: return null
    }
  }
//
//  fun <T> AtomicArray<T>.putRandom(size: Int, value: T, repeats: Int): Boolean {
//    repeat(repeats) {
//      val index = random.nextInt(size)
//      if (this[index].compareAndSet(null, value)) return true
//    }
//    return false
//  }
//
//  fun <T> AtomicArray<T>.popRandom(size: Int, repeats: Int): T? {
//    for (i in 0 until repeats) {
//      val index = random.nextInt(size)
//      return this[index].getAndSet(null) ?: continue
//    }
//    return null
//  }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val REPEATS = 10000