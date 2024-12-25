//package mpp.dynamicarray

import kotlinx.atomicfu.*
import mpp.dynamicarray.CoreElement.Element
import mpp.dynamicarray.CoreElement.Moved

interface DynamicArray<E> {
  /**
   * Returns the element located in the cell [index],
   * or throws [IllegalArgumentException] if [index]
   * exceeds the [size] of this array.
   */
  fun get(index: Int): E

  /**
   * Puts the specified [element] into the cell [index],
   * or throws [IllegalArgumentException] if [index]
   * exceeds the [size] of this array.
   */
  fun put(index: Int, element: E)

  /**
   * Adds the specified [element] to this array
   * increasing its [size].
   */
  fun pushBack(element: E)

  /**
   * Returns the current size of this array,
   * it increases with [pushBack] invocations.
   */
  val size: Int

}

class DynamicArrayImpl<E> : DynamicArray<E> {
  private val core = atomic(Core<E>(INITIAL_CAPACITY))

  override fun get(index: Int): E {
    if (index !in 0 until size) throw IllegalArgumentException("Index $index is out of bounds")
    while (true) {
      when (val currentElement = core.value.get(index)) {
        is Element<E> -> return currentElement.value
        is Moved<E> -> copyWithMoreCapacity()
      }
    }
  }

  override fun put(index: Int, element: E) {
    if (index !in 0 until size) throw IllegalArgumentException("Index $index is out of bounds")
    while (true) {
      when (val currentElement = core.value.get(index)) {
        is Element<E> -> {
          if (core.value.cas(index, currentElement, Element(element))) return
        }
        is Moved<E> -> copyWithMoreCapacity()
      }
    }
  }

  override fun pushBack(element: E) {
    while (true) {
      val currentCore = core.value
      val currentSize = currentCore.size.getAndIncrement()
      if (currentSize < currentCore.capacity) {
        if (currentCore.cas(currentSize, null, Element(element))) {
          return
        }
      } else {
        copyWithMoreCapacity()
      }
    }
  }

  override val size: Int get() = core.value.size.value

  private fun copyWithMoreCapacity() {
    val currentCore = core.value
    val currentSize = currentCore.size.value
    if (currentSize < currentCore.capacity) return
    core.value.nextCore.compareAndSet(null, Core(currentCore.capacity * 2))
    val nextCore = core.value.nextCore.value!!
    val idx = currentCore.moved.getAndIncrement()
    if (idx >= currentCore.capacity) return
    val currentElement = currentCore.get(idx)
    nextCore.put(idx, currentElement)
    currentCore.put(idx, Moved())
  }
}

sealed class CoreElement<E> {
  class Moved<E> : CoreElement<E>()
  class Element<E>(val value: E) : CoreElement<E>()
}

private class Core<E>(
  val capacity: Int,
) {
  val nextCore = atomic<Core<E>?>(null)
  private val array = atomicArrayOfNulls<CoreElement<E>>(capacity)
  val size = atomic(0)
  val moved = atomic(0)
  @Suppress("UNCHECKED_CAST")
  fun get(index: Int): CoreElement<E> {
    return array[index].value as CoreElement<E>
  }

  fun put(index: Int, element: CoreElement<E>) {
    array[index].value = element
  }

  fun cas(index: Int, currentElement: Element<E>?, element: Element<E>): Boolean =
    array[index].compareAndSet(currentElement, element)


}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME