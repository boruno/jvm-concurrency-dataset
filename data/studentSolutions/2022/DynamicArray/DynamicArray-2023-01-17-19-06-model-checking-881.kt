package mpp.dynamicarray

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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
        if (index < 0 || index >= core.value.size()) throw IllegalArgumentException()
        return core.value.get(index)!!.elem
    }

    override fun put(index: Int, element: E) {
        var currCore = core.value
        if (index < 0 || index >= currCore.size()) throw IllegalArgumentException()

        currCore.array[index].value = CoreElementRecreate(element)
//
//        while (true) {
//            val newCore = newCore.value
//            if (newCore != null){
//                val currSome = newCore.array[index].value
//
//                newCore.array[index].compareAndSet(null, currSome)
//                return
//            } else return
//        }

        while (true) {
            val nextNode = currCore.recursiveCore.value ?: break
            val currNextElem = currCore.array[index].value ?: break

            nextNode.array[index].value = currNextElem
            currCore = nextNode
        }
    }

    override fun pushBack(element: E) {
        val newElem = CoreElementRecreate(element)
        while (true) {
            val currCore = core.value
            val coreSize = currCore.size()
            val currCap = currCore.capacitySumSize
            if ((coreSize + 1) > currCap) {
                var newCore = Core<E>(currCap * 2)
                newCore.updMainSize(coreSize)
                val isEmptyNewCore = currCore.recursiveCore.compareAndSet(null, newCore)
                if (isEmptyNewCore) newCore = currCore.recursiveCore.value ?: continue
                for (idx in 0 until currCap) {
                    val currCoreElem = currCore.get(idx)!!
                    //val currValue = currCoreElem.putAtomicStatus.value
                    //currCore.get(idx)!!.putAtomicStatus.compareAndSet(null, Any())
                    newCore.array[idx].compareAndSet(null, CoreElementRecreate(currCoreElem.elem))
                }
                core.compareAndSet(currCore, newCore)

//                for (idx in 0 until coreSize) {
//                    val currCoreElem = currCore.get(idx)!!
//                    newCore.array.get(idx).compareAndSet(null, CoreElementRecreate(currCoreElem.elem))
//                }
//                core.compareAndSet(currCore, newCore)
            } else {
                val currSuccess = currCore.array.get(coreSize).compareAndSet(null, newElem)
                currCore._size.compareAndSet(coreSize, coreSize + 1) // просто inc плохо работает
                if (currSuccess) return
//                if (currCore.array.get(coreSize).compareAndSet(null, newElem)) {
//                    currCore._size.compareAndSet(coreSize, coreSize + 1) // просто inc плохо работает
//                    return
//                } else currCore._size.compareAndSet(coreSize, coreSize + 1)
            }
        }
    }

    override val size: Int get() = core.value.size()
}

private class CoreElementRecreate<E>(val elem: E) {
   // val putAtomicStatus = atomic<Any?>(null)
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<CoreElementRecreate<E>>(capacity)
    val recursiveCore: AtomicRef<Core<E>?> = atomic(null)
    val _size = atomic(0)
    private val _capSize = atomic(capacity)

    fun size(): Int = _size.value

    //val size: Int = _size.value
    val capacitySumSize: Int = _capSize.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): CoreElementRecreate<E>? {
        //require(index < size())
        return array[index].value
    }

    fun getAtomic(index: Int): CoreElementRecreate<E>? {
        require(index < size())
        return array[index].value
    }

    fun incMainSize() = _size.incrementAndGet()
    fun updMainSize(updSize: Int) {
        _size.value = updSize
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME