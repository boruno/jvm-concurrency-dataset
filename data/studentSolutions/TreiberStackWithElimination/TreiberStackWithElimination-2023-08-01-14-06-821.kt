//package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*

class TreiberStackWithElimination<E> : Stack<E> {
  private val stack = TreiberStack<E>()

  // TODO: Try to optimize concurrent push and pop operations,
  // TODO: synchronizing them in an `eliminationArray` cell.
  private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

  override fun push(element: E) {
    if (tryPushElimination(element)) return
    stack.push(element)
  }

  private fun tryPushElimination(element: E): Boolean {

    val cellIndex = randomCellIndex()
    val cell = eliminationArray[cellIndex]

    while (true) {
      if (cell.compareAndSet(CELL_STATE_EMPTY, element)) break
    }
    for (i in (0 until ELIMINATION_WAIT_CYCLES)) {
        if (cell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) return true
//      val oldVal = cell.getAndUpdate { oldValue ->
//        if (oldValue == CELL_STATE_RETRIEVED) {
//          CELL_STATE_EMPTY
//        } else if (i == ELIMINATION_WAIT_CYCLES - 1) {
//          CELL_STATE_EMPTY
//        } else oldValue
//      }
//      if (oldVal == CELL_STATE_RETRIEVED) return true
    }

      cell.value = CELL_STATE_EMPTY
      return false

//    val oldValue = cell.getAndUpdate { v ->
//        if (v == CELL_STATE_EMPTY) element
//        else v
//    }
//
//      if (oldValue == CELL_STATE_EMPTY) {
//
//      }

    
    // TODO: Choose a random cell in `eliminationArray`
    // TODO: and try to install the element there.
    // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
    // TODO: in hope that a concurrent `pop()` grabs the
    // TODO: element. If so, clean the cell and finish,
    // TODO: returning `true`. Otherwise, move the cell
    // TODO: to the empty state and return `false`.
  }

  override fun pop(): E? = tryPopElimination() ?: stack.pop()

  private fun tryPopElimination(): E? {
    val cellIndex = randomCellIndex()
    val cell = eliminationArray[cellIndex]

//    for (i in (0 until ELIMINATION_WAIT_CYCLES)) {
    val oldValue = cell.getAndUpdate { v ->
      if (v == CELL_STATE_EMPTY) CELL_STATE_EMPTY
      else if (v == CELL_STATE_RETRIEVED) CELL_STATE_RETRIEVED
      else CELL_STATE_RETRIEVED
    }
    return when (oldValue) {
      CELL_STATE_EMPTY -> null
      CELL_STATE_RETRIEVED -> null
      else -> oldValue as E
    }
//    }

//    return null


    // TODO: Choose a random cell in `eliminationArray`
    // TODO: and try to retrieve an element from there.
    // TODO: On success, return the element.
    // TODO: Otherwise, if the cell is empty, return `null`.
  }

  private fun randomCellIndex(): Int =
    ThreadLocalRandom.current().nextInt(eliminationArray.size)

  companion object {
    private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
    private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

    // Initially, all cells are in EMPTY state.
    private val CELL_STATE_EMPTY = null

    // `tryPopElimination()` moves the cell state
    // to `RETRIEVED` if the cell contains element.
    private val CELL_STATE_RETRIEVED = Any()
  }
}