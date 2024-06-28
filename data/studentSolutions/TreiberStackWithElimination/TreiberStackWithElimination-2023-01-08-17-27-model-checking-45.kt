package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.concurrent.thread

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Pair<E>>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true){
            val oldTop = top.value
            if (top.compareAndSet(oldTop, Node(x,oldTop))){
                return
            }else{
                val rnd = (0 ..1).random()
                if(eliminationArray[rnd].compareAndSet(null,Pair(x))){
                    for (i in (0..20)){
                        if(eliminationArray[rnd].value!!.done){
                            val pair = eliminationArray[rnd].value
                            eliminationArray[rnd].compareAndSet(pair,null)
                            return
                        }
                    }
                    val pair = eliminationArray[rnd].value
                    if(!eliminationArray[rnd].compareAndSet(pair,null)){
                        val pair = eliminationArray[rnd].value
                        eliminationArray[rnd].compareAndSet(pair,null)
                        return
                    }

                }
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var oldTop = top.value
        while (true){
            oldTop = top.value
            oldTop ?: return null
            if(top.compareAndSet(oldTop,oldTop!!.next)){
                return oldTop.x
            }else{

            }
        }
    }

    class Pair<E>(x: E) {
        val x = x
        val done = false
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT