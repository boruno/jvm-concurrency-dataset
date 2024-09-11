import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
  private val a = List<Ref<Any?>>(size) { Ref(initialValue) }

  fun get(index: Int) =
    a[index].value as E

  fun cas(index: Int, expected: E, update: E) =
    a[index].cas(expected, update)


  fun cas2(
    index1: Int, expected1: E, update1: E,
    index2: Int, expected2: E, update2: E
  ): Boolean = if (index1 > index2) {
    cas2(index2, expected2, update2, index1, expected1, update1)
  } else if (index1 == index2) {
    if (update1 == expected2) {
      cas(index1, expected1, update2)
    } else {
      false
    }
  } else {
    val a1 = a[index1]
    val b1 = a[index2]
    val desc = NDescriptor(a1, expected1, update1, b1, expected2, update2)
    if (a1.cas(expected1, desc)) desc.complete() else false
  }
}

fun dcss(
  a: Ref<Any?>,
  expectA: Any?,
  updateA: Any?,
  b: Ref<Any?>,
  expectB: Any?
): Boolean {
  val a1 = if (b.v.value == expectB) updateA else expectA
  return a.v.compareAndSet(expectA, a1) // FAKE DON'T TRUST BUT VRODE NE NYZHNO
}

abstract class Descriptor {
  abstract fun complete(): Boolean
}

class Ref<T>(initial: T) {
  val v = atomic<Any?>(initial)

  var value: T
    get() {
      v.loop {
        when (it) {
          is Descriptor -> it.complete()
          else -> return it as T
        }
      }
    }
    set(value) {
      v.loop {
        when (it) {
          is Descriptor -> it.complete()
          else -> if (v.compareAndSet(it, value)) return
        }
      }
    }

  fun cas(expectA: Any?, updateA: Any?): Boolean {
    v.loop {
      when {
        it is Descriptor -> it.complete()
        it != expectA -> return false
        else -> if (v.compareAndSet(expectA, updateA)) return true
      }
    }
  }

}

//class RDCSSDescriptor(
//  var a: Ref<Any?>,
//  var expectA: Any?,
//  var updateA: Any?,
//  var b: Ref<Any?>,
//  var expectB: Any?
//) : Descriptor() {
//  init {
//    if (updateA == null) updateA = this
//    if (expectA == null) expectA = this
//  }
//
//  override fun complete(): Boolean {
//    val a1 = if (b.v.value == expectB) updateA else expectA
//    return a.v.compareAndSet(this, a1)
//  }
//}

class NDescriptor(
  private val a: Ref<Any?>,
  private val expectA: Any?,
  private val updateA: Any?,
  private val b: Ref<Any?>,
  private val expectB: Any?,
  private val updateB: Any?,
) : Descriptor() {
  private val state = Ref<Boolean?>(null)
  override fun complete(): Boolean {
    if (b.v.value != this) dcss(b, expectB, this, state as Ref<Any?>, null)
    state.v.compareAndSet(null, b.v.value == this)
    val (a1, b1) = if (state.value == true) updateA to updateB else expectA to expectB
    a.v.compareAndSet(this, a1)
    b.v.compareAndSet(this, b1)
    return state.value!!
  }
}