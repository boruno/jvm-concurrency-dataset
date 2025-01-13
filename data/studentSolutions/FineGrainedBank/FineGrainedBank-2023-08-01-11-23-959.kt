@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.concurrent.ConcurrentHashMap

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }
    private val accountLocks: ConcurrentHashMap<Int, ReentrantLock> = ConcurrentHashMap()

    private fun getLockForAccount(id: Int): ReentrantLock =
        accountLocks.getOrPut(id) { ReentrantLock() }

    override fun getAmount(id: Int): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        val account = accounts[id]
        val lock = getLockForAccount(id)
        try {
            return account.amount
        }
        finally {
            lock.unlock()
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        val lock = getLockForAccount(id)
        lock.lock()
        try {
            require(amount > 0) { "Invalid amount: $amount" }
            val account = accounts[id]
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            return account.amount
        }
        finally {
            lock.unlock()
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        val lock = getLockForAccount(id)
        lock.lock()
        try {
            require(amount > 0) { "Invalid amount: $amount" }
            val account = accounts[id]
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            return account.amount
        }
        finally {
            lock.unlock()
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via fine-grained locking.
        val fromLock = getLockForAccount(fromId)
        val toLock = getLockForAccount(toId)
        fromLock.lock()
        toLock.lock()
        try {
            require(amount > 0) { "Invalid amount: $amount" }
            require(fromId != toId) { "fromId == toId" }
            val from = accounts[fromId]
            val to = accounts[toId]
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
        }
        finally {
            toLock.unlock()
            fromLock.unlock()
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