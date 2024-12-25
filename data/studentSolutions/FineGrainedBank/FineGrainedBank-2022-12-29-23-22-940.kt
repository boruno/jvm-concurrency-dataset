//package mpp.fgbank

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.*
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.concurrent.locks.ReentrantLock

class FineGrainedBank(n: Int) {
    private val accounts: Array<Account> = Array(n) { Account() }

    /**
     * Returns current amount in the specified account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @return amount in account.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     */
    fun getAmount(index: Int): Long {
        var result: Long = 0
        accounts[index].withLock {
            result = accounts[index].amount
        }
        return result
    }

    private fun Array<Account>.withLock(index: Int = 0, lambda: () -> Unit) {
        if (index == this.size) {
            lambda()
        } else {
            this[index].withLock {
                this.withLock(index + 1, lambda)
            }
        }
    }

    /**
     * Returns total amount deposited in this bank.
     */
    fun totalAmount(): Long {
        var result = 0L
        accounts.withLock {
            result = accounts.sumOf { it.amount }
        }
        return result
    }

    /**
     * Deposits specified amount to account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @param amount positive amount to deposit.
     * @return resulting amount in account.
     * @throws IllegalArgumentException when amount <= 0.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     * @throws IllegalStateException when deposit will overflow account above [MAX_AMOUNT].
     */
    fun deposit(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        var result = 0L
        accounts[index].withLock {
            val account = accounts[index]
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            result = account.amount
        }
        return result
    }

    /**
     * Withdraws specified amount from account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @param amount positive amount to withdraw.
     * @return resulting amount in account.
     * @throws IllegalArgumentException when amount <= 0.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     * @throws IllegalStateException when account does not enough to withdraw.
     */
    fun withdraw(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        var result = 0L
        accounts[index].withLock {
            val account = accounts[index]
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            result = account.amount
        }
        return result
    }

    /**
     * Transfers specified amount from one account to another account.
     *
     * @param fromIndex account index to withdraw from.
     * @param toIndex account index to deposit to.
     * @param amount positive amount to transfer.
     * @throws IllegalArgumentException when amount <= 0 or fromIndex == toIndex.
     * @throws IndexOutOfBoundsException when account indices are invalid.
     * @throws IllegalStateException when there is not enough funds in source account or too much in target one.
     */
    fun transfer(fromIndex: Int, toIndex: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromIndex != toIndex) { "fromIndex == toIndex" }
        val minIndex = min(fromIndex, toIndex)
        val maxIndex = max(fromIndex, toIndex)
        accounts[minIndex].withLock {
            accounts[maxIndex].withLock {
                val from = accounts[fromIndex]
                val to = accounts[toIndex]
                check(amount <= from.amount) { "Underflow" }
                check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
                from.amount -= amount
                to.amount += amount
            }
        }
    }

    /**
     * Private account data structure.
     */
    class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0

        private val roflLock = atomic(false)


        fun withLock(lambda: () -> Unit) {
            if (roflLock.compareAndSet(false, true)) {
                try {
                    lambda()
                } finally {
                    roflLock.getAndSet(false)
                }
            }
//            lock.withLock(lambda)
        }

    }
}

/**
 * The maximal amount that can be kept in a bank account.
 */
private const val MAX_AMOUNT = 1000000000000000L
