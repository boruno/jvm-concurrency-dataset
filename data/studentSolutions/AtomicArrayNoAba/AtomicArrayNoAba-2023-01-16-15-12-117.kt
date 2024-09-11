import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
  private val a = atomicArrayOfNulls<Pair<Boolean, E>>(size)

  init {
    for (i in 0 until size) a[i].value = false to initialValue
  }

  fun get(index: Int) =
    a[index].value!!.second

  fun cas(index: Int, expected: E, update: E) =
    a[index].compareAndSet(false to expected, false to update)


  fun cas2(
    index1: Int, expected1: E, update1: E,
    index2: Int, expected2: E, update2: E
  ): Boolean {
    while (true) {
      // TODO this implementation is not linearizable,
      // TODO a multi-word CAS algorithm should be used here.
      if (a[index1].value?.first == true) continue
      if (a[index2].value?.first == true) continue
      if (!a[index1].compareAndSet(false to expected1, true to expected1)) return false
      if (!a[index2].compareAndSet(false to expected2, true to expected2)) {
        a[index1].compareAndSet(true to expected1, false to expected1)
        return false
      }
      if (!a[index1].compareAndSet(true to expected1,  true to update1)) {
        a[index1].compareAndSet(true to expected1, false to expected1)
        a[index2].compareAndSet(true to expected2, false to expected2)
        continue
      }
      if (!a[index2].compareAndSet(true to expected2, false to update2)) {
        a[index1].compareAndSet(true to update1, false to expected1)
        a[index2].compareAndSet(true to expected2, false to expected2)
        continue
      }
      return true
    }
  }

  sealed class Node<E> {
    class Value<E>(val value: E) : Node<E>()
    class Marked<E>(val value: E) : Node<E>()

    class Changed<E>(val from: E, val value: E) : Node<E>()
  }
}