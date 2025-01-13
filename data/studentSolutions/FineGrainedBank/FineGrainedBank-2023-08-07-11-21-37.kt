@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*
import kotlin.math.min

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    private val locks: Array<ReentrantLock> = Array(accountsNumber) { ReentrantLock() }

    override fun getAmount(id: Int): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        return fineLocked(id) {
            val account = accounts[id]
            account.amount
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        return fineLocked(id) {
            val account = accounts[id]
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            account.amount
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        return fineLocked(id) {
            val account = accounts[id]
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            account.amount
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }
        fineLocked(fromId) {
            val from = accounts[fromId]
            val to = accounts[toId]
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
        }
    }

    private fun <T> fineLocked(id: Int, id2: Int? = null, block: () -> T): T {
        require(id in locks.indices) { "OOB: $id"}
        locks[id].lock()
        if (id2 == null) {
            return try {
                block()
            }
            finally {
                locks[id].unlock()
            }
        }
        val realId = minOf(id, id2)
        locks[realId].lock()
        return try {
            block()
        }
        finally {
            locks[realId].unlock()
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

        /**
         * TODO: use this mutex to protect the account data.
         */
        val lock = ReentrantLock()
    }
}