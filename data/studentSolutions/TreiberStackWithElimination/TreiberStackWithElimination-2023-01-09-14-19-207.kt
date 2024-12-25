//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
import java.util.Timer

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Operation<E?>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val zeroOperation = Operation<E?>("", null)
        val randomInteger = ThreadLocalRandom.current().nextInt(0, ELIMINATION_ARRAY_SIZE)
        val curArrElement = eliminationArray[randomInteger].value
        val newOperation = Operation<E?>("push", x)
        if ((curArrElement == null || curArrElement.name == "" || curArrElement.name == "pop") &&
                eliminationArray[randomInteger].compareAndSet(curArrElement, newOperation)){
            for (i in 0..100){
                val tempArrElement = eliminationArray[randomInteger].value
                if (tempArrElement!!.name == "DONE"){
                    eliminationArray[randomInteger].compareAndSet(tempArrElement, zeroOperation)
                    return
                }
            }
            eliminationArray[randomInteger].compareAndSet(newOperation, zeroOperation)
        }

        while(true) {
            val curTop = top.value
            val newTop = Node<E>(x, curTop)
            if (top.compareAndSet(curTop, newTop)) {
                return
            }
        }
    }
    
    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val zeroOperation = Operation<E?>("", null)
        val randomInteger = ThreadLocalRandom.current().nextInt(0, ELIMINATION_ARRAY_SIZE)
        val curArrElement = eliminationArray[randomInteger].value
        val doneOperation = Operation<E?>("DONE", null)
        val popOperation = Operation<E?>("pop", null)
        if ((curArrElement == null || curArrElement.name == "") &&
            eliminationArray[randomInteger].compareAndSet(curArrElement, popOperation)){
            for (i in 0..100){
                val tempArrElement = eliminationArray[randomInteger].value
                if (tempArrElement!!.name == "push"){
                    eliminationArray[randomInteger].compareAndSet(tempArrElement, zeroOperation)
                    return tempArrElement.x
                }
            }
            eliminationArray[randomInteger].compareAndSet(popOperation, zeroOperation)
        } else if (curArrElement != null && curArrElement.name == "push" &&
                eliminationArray[randomInteger].compareAndSet(curArrElement, doneOperation)){
            return curArrElement.x
        }
        while(true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }
}

fun main(){
    val st = TreiberStackWithElimination<Int>()
    println(st.pop())
    println(st.push(-4))
    println(st.push(-8))
    println(st.pop())
}

private class Node<E>(val x: E, val next: Node<E>?)

private class Operation<E>(val name: String, val x: E)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT