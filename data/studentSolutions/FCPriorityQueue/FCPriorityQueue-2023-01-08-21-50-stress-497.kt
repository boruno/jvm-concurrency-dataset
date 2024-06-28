import kotlinx.atomicfu.AtomicBoolean
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
            val locked = tryLock()
            if (locked) {
                val operation = OperationData<E>(OperationType.POLL, null)
                val index = Random.nextInt(ARRAY_SIZE)
                if (fc_array[index].compareAndSet(null, operation)) {
                    repeat(1000){

                    }
                    if(fc_array[index].value!!.operationType == OperationType.DONE) {
                        val result = fc_array[index].value!!.operationValue
                        operation.operationValue = result
                        operation.operationType = OperationType.DONE
                        fc_array[index].compareAndSet(operation, null)
                        return result
                    }
                    else
                    {
                        fc_array[index].compareAndSet(operation, null)
                    }
                }
            }
            else{
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
            val locked = tryLock()
            if (locked) {
                val operation = OperationData<E>(OperationType.PEEK, null)
                val index = Random.nextInt(ARRAY_SIZE)
                if (fc_array[index].compareAndSet(null, operation)) {
                    repeat(1000){

                    }
                    if(fc_array[index].value!!.operationType == OperationType.DONE){
                        val result = fc_array[index].value!!.operationValue
                        operation.operationValue = result
                        operation.operationType = OperationType.DONE
                        fc_array[index].compareAndSet(operation, null)
                        return result
                    }
                    else
                    {
                        fc_array[index].compareAndSet(operation, null)
                    }
                }
            }
            else{
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
            val locked = tryLock()
            if (locked) {
                val operation = OperationData<E>(OperationType.ADD, element)
                val index = Random.nextInt(ARRAY_SIZE)
                if (fc_array[index].compareAndSet(null, operation)) {
                    repeat(1000){

                    }
                    if(fc_array[index].value!!.operationType == OperationType.DONE){
                        val result = fc_array[index].value!!.operationValue
                        operation.operationValue = result
                        operation.operationType = OperationType.DONE
                        fc_array[index].compareAndSet(operation, null)
                        return
                    }
                    else
                    {
                        fc_array[index].compareAndSet(operation, null)
                    }

                }
            }
            else{
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
        isLocked.compareAndSet(true, false)
    }

    fun checkArray(){
        for(i in 0 until ARRAY_SIZE){
            if(fc_array[i].value == null)
                continue
            if(fc_array[i].value!!.operationType == OperationType.POLL){
                fc_array[i].value!!.operationType = OperationType.DONE
                fc_array[i].value!!.operationValue = q.poll()
                continue
            }
            if(fc_array[i].value!!.operationType == OperationType.ADD){
                fc_array[i].value!!.operationType = OperationType.DONE
                q.add(fc_array[i].value!!.operationValue)
                continue
            }
            if(fc_array[i].value!!.operationType == OperationType.PEEK){
                fc_array[i].value!!.operationType = OperationType.DONE
                fc_array[i].value!!.operationValue = q.peek()
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

const val ARRAY_SIZE = 100