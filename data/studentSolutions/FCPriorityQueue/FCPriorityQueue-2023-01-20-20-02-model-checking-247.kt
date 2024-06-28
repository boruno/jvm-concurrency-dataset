import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
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
        while(true) {
            if (!tryLock()) {
                val index = Random.nextInt(ARRAY_SIZE)
                val operation = OperationData<E>(OperationType.POLL, null)
                if(fc_array[index].compareAndSet(null,operation)){
                    if(fc_array[index].value!!.operationType == OperationType.DONE){
                        val op = fc_array[index].value
                        val res = op!!.operationValue
                        fc_array[index].compareAndSet(op, null)
                        return res
                    }
                }
            }
            else {
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
        while(true) {
            if (!tryLock()) {
                val index = Random.nextInt(ARRAY_SIZE)
                val operation = OperationData<E>(OperationType.POLL, null)
                if(fc_array[index].compareAndSet(null,operation)){
                    if(fc_array[index].value!!.operationType == OperationType.DONE){
                        val op = fc_array[index].value
                        val res = op!!.operationValue
                        fc_array[index].compareAndSet(op, null)
                        return res
                    }
                }
            }
            else {
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
        while(true) {
            if (!tryLock() ) {
                val index = Random.nextInt(ARRAY_SIZE)
                val operation = OperationData<E>(OperationType.POLL, null)
                if(fc_array[index].compareAndSet(null,operation)){
                    if(fc_array[index].value!!.operationType == OperationType.DONE){
                        val op = fc_array[index].value
                        fc_array[index].compareAndSet(op, null)
                        return
                    }
                }
            }
            else {
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
                    val operationResult = OperationData<E>(OperationType.DONE, res)
                    fc_array[i].compareAndSet(operationData, operationResult)
                    continue
            }
            else if(operationData.operationType == OperationType.ADD){
                val operationResult = OperationData<E>(OperationType.DONE, operationData.operationValue)
                q.add(operationData.operationValue)
                fc_array[i].compareAndSet(operationData,operationResult)
                continue
            }
            else if(operationData.operationType == OperationType.PEEK){
                val res = q.peek()
                val operationResult = OperationData<E>(OperationType.DONE, res)
                fc_array[i].compareAndSet(operationData,operationResult)
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

const val ARRAY_SIZE = 10