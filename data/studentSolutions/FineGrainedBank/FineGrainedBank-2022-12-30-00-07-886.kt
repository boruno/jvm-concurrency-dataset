//package mpp.fgbank

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.*
import kotlin.IllegalStateException
import kotlin.math.max
import kotlin.math.min

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
        if (index < 0 || index >= accounts.size) {
            throw IndexOutOfBoundsException()
        }
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
        if (amount <= 0) {
            throw IllegalArgumentException()
        }
        if (index < 0 || index >= accounts.size) {
            throw IndexOutOfBoundsException()
        }
        var result = 0L
        accounts[index].withLock {
            val account = accounts[index]
            if (account.amount + amount > MAX_AMOUNT) {
                throw IllegalStateException()
            }
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
        if (amount <= 0) {
            throw IllegalArgumentException()
        }
        if (index < 0 || index >= accounts.size) {
            throw IndexOutOfBoundsException()
        }

        var result = 0L
        accounts[index].withLock {
            val account = accounts[index]
            if (account.amount - amount < 0) {
                throw IllegalStateException()
            }
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
        if (amount <= 0 || fromIndex == toIndex) {
            throw IllegalArgumentException()
        }
        if (fromIndex < 0 || fromIndex >= accounts.size || toIndex < 0 || toIndex >= accounts.size) {
            throw IndexOutOfBoundsException()
        }
        val minIndex = min(fromIndex, toIndex)
        val maxIndex = max(fromIndex, toIndex)
        accounts[minIndex].withLock {
            accounts[maxIndex].withLock {
                val from = accounts[fromIndex]
                val to = accounts[toIndex]
                if (from.amount - amount < 0 || to.amount + amount > MAX_AMOUNT) {
                    throw IllegalStateException()
                }
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
            while (true) {
                if (roflLock.compareAndSet(false, true)) {
                    try {
                        lambda()
                    } finally {
                        roflLock.getAndSet(false)
                        return
                    }
                }
            }
        }

    }
}

/**
 * The maximal amount that can be kept in a bank account.
 */
private const val MAX_AMOUNT = 1000000000000000L
