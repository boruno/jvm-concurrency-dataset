import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random


class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val isLocked : AtomicBoolean = atomic(false)
    val fc_array  = atomicArrayOfNulls<OperationData<E>>(ARRAY_SIZE)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        val operation = OperationData<E>(OperationType.POLL, null)
        while(true) {
            if(!fc_array[index].compareAndSet(null,operation)){
                index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
                continue
            }
            if (!tryLock()) {
                if(fc_array[index].value == operation){
                    continue
                }
                else if(fc_array[index].value!!.operationType == OperationType.POLL){
                    val op = fc_array[index].value
                    fc_array[index].compareAndSet(op, null)
                    return op!!.operationValue
                }
            }
            else {
                fc_array[index].compareAndSet(operation,null)
                val res = q.poll()
                checkArray()
                unlock()
                return res
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        val operation = OperationData<E>(OperationType.PEEK, null)
        while(true) {
            if(!fc_array[index].compareAndSet(null,operation)){
                index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
                continue
            }
            if (!tryLock()) {
                if(fc_array[index].value == operation){
                    continue
                }
                else if(fc_array[index].value!!.operationType == OperationType.PEEK){
                    val op = fc_array[index].value
                    fc_array[index].compareAndSet(op, null)
                    return op!!.operationValue
                }
            }
            else {
                fc_array[index].compareAndSet(operation,null)
                val res = q.peek()
                checkArray()
                unlock()
                return res
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        val operation = OperationData<E>(OperationType.ADD, element)
        while(true) {
            if(!fc_array[index].compareAndSet(null,operation)){
                index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
                continue
            }
            if (!tryLock() ) {
                if(fc_array[index].compareAndSet(null,operation)){
                    continue
                }
                else if(fc_array[index].value == operation){
                    continue
                }
                else if(fc_array[index].value!!.operationType == OperationType.ADD){
                    val op = fc_array[index].value
                    fc_array[index].compareAndSet(op, null)
                    return
                }
            }
            else {
                fc_array[index].compareAndSet(operation,null)
                q.add(element)
                checkArray()
                unlock()
                return
            }
        }
    }

    fun tryLock(): Boolean {
        return isLocked.compareAndSet(false,true)
    }

    fun unlock(){
        isLocked.compareAndSet(true,false)
    }


    fun checkArray(){
        for(i in 0 until ARRAY_SIZE){
            val operationData = fc_array[i].value ?: continue
            if(operationData.operationType == OperationType.POLL){
                val res = q.poll()
                fc_array[i].value!!.operationValue = res
                continue
            }
            else if(operationData.operationType == OperationType.ADD){
                q.add(operationData.operationValue)
                fc_array[i].value!!.operationValue = null
                continue
            }
            else if(operationData.operationType == OperationType.PEEK){
                val res = q.peek()
                fc_array[i].value!!.operationValue = res
                continue
            }
        }
    }
}

enum class OperationType{
    ADD,
    POLL,
    PEEK,
    DONE
}
class OperationData<E>(type: OperationType, value: E?)
{
    var operationType = type
    var operationValue = value
}

const val ARRAY_SIZE = 4