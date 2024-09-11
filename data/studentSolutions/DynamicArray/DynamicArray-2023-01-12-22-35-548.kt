package mpp.dynamicarray

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
        else -> throw IllegalStateException("Element was removed")
      }
    }
  }

  override fun put(index: Int, element: E) {
    if (index !in 0 until size) throw IllegalArgumentException("Index $index is out of bounds")
    while (true) {
      with(core.value) {
        if (size.value >= capacity) {
          copyWithMoreCapacity()
        } else {
          when (val currentElement = core.value.get(index)) {
            is Element<E> -> {
              if (core.value.cas(index, currentElement, Element(element))) {
//            println("put: $element at $index")
                return
              }
            }

            is Moved<E> -> copyWithMoreCapacity()
            else -> throw IllegalStateException("Element was removed")
          }
        }
      }
    }
  }

  override fun pushBack(element: E) {
    while (true) {
      val currentCore = core.value
      val currentSize = currentCore.size.value
      if (currentSize < currentCore.capacity) {
        if (core.value.cas(currentSize, null, Element(element))) {
//          println("pushBack: $element at $currentSize")
          core.value.size.compareAndSet(currentSize, currentSize + 1)
          return
        } else {
//          println("help pushBack")
          core.value.size.compareAndSet(currentSize, currentSize + 1)
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
    val currentCapacity = currentCore.capacity
    if (currentSize < currentCapacity) return
    currentCore.nextCore.compareAndSet(null, Core(currentCapacity * 2, currentSize))
    val nextCore = currentCore.nextCore.value!!
    for (i in 0 until currentCapacity) {
      while (true) {
        val currentElement = currentCore.get(i) ?: break
        val nextElement = nextCore.get(i)
        if (nextCore.cas(i, nextElement, currentElement)) {
          if (currentCore.cas(i, currentElement, Moved())) {
            break
          }
        }
      }
    }
    core.compareAndSet(currentCore, nextCore)
  }
}

sealed class CoreElement<E> {
  class Moved<E> : CoreElement<E>()
  class Element<E>(val value: E) : CoreElement<E>()
}

private class Core<E>(
  val capacity: Int,
  _size: Int = capacity / 2
) {
  val nextCore = atomic<Core<E>?>(null)
  private val array = atomicArrayOfNulls<CoreElement<E>>(capacity)
  val size = atomic(_size)

  @Suppress("UNCHECKED_CAST")
  fun get(index: Int): CoreElement<E>? {
    return array[index].value
  }

  fun put(index: Int, element: CoreElement<E>) {
    array[index].value = element
  }

  fun cas(index: Int, currentElement: CoreElement<E>?, element: CoreElement<E>): Boolean =
    array[index].compareAndSet(currentElement, element)


}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME