//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<EliminationRecord<E>>(ELIMINATION_ARRAY_SIZE)
    val elimIndex = atomic(0)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val elimIdx = elimIndex.getAndIncrement()
        val eliminationRecord = EliminationRecord(x, EliminationState.PUSH, elimIdx)
        val index = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        while (true) {
            if(eliminationArray[index].compareAndSet(null, eliminationRecord)){
                continue
            }
            if(eliminationArray[index].value!!.state == EliminationState.DONE && eliminationArray[index].value!!.elimIndex == elimIdx){
                val cur = eliminationArray[index].value
                eliminationArray[index].compareAndSet(cur, null)
                return
            }
            if(eliminationArray[index].value!!.state == EliminationState.POP){
                val curRecord = eliminationArray[index].value
                val newRecord = EliminationRecord(x, EliminationState.DONE, elimIdx)
                if(eliminationArray[index].compareAndSet(curRecord, newRecord))
                    return
            }
            if(eliminationArray[index].value!!.state == EliminationState.PUSH && eliminationArray[index].value!!.elimIndex == elimIdx){
                if(!eliminationArray[index].compareAndSet(eliminationRecord, null))
                    continue
            }
            val curr_head = top.value
            val new_head = Node(x, curr_head)
            if (top.compareAndSet(curr_head, new_head))
                return
        }
   }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val elimIdx = elimIndex.getAndIncrement()
        val eliminationRecord = EliminationRecord<E>(null, EliminationState.POP, elimIdx)
        val index = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        while(true) {
            if(eliminationArray[index].compareAndSet(null, eliminationRecord)){
                continue
            }
            if(eliminationArray[index].value!!.state == EliminationState.DONE && eliminationArray[index].value!!.elimIndex == elimIdx){
                val cur = eliminationArray[index].value
                eliminationArray[index].compareAndSet(cur, null)
                return cur!!.x
            }
            if(eliminationArray[index].value!!.state == EliminationState.PUSH){
                val curRecord = eliminationArray[index].value
                val newRecord = EliminationRecord<E>(null, EliminationState.DONE, elimIdx)
                if(eliminationArray[index].compareAndSet(curRecord, newRecord))
                    return curRecord!!.x
            }
            if(eliminationArray[index].value!!.state == EliminationState.POP && eliminationArray[index].value!!.elimIndex == elimIdx){
                if(!eliminationArray[index].compareAndSet(eliminationRecord, null))
                    continue
            }
            val curr_head = top.value ?: return null
            val curr_head_next = curr_head.next
            if(top.compareAndSet(curr_head, curr_head_next))
                return curr_head.x
        }
    }
}


private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
 enum class EliminationState{
    DONE,
    PUSH,
    POP,
}

class EliminationRecord<E>(val x: E?, val state: EliminationState, val elimIndex: Int){
}