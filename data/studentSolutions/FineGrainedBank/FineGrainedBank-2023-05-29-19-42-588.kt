@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    // TODO: use this mutex to protect all bank operations.
    private val locks = Array(accountsNumber) { ReentrantLock() }

    private inline fun <T> ReentrantLock.doSafe(task: () -> T): T {
        lock()
        try {
            return task()
        }
        finally {
            unlock()
        }
    }

    override fun getAmount(id: Int): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        val lock = locks[id]
        return lock.doSafe {
            accounts[id].amount
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val lock = locks[id]
        return lock.doSafe {
            val account = accounts[id]
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            account.amount
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val lock = locks[id]
        return lock.doSafe {
            val account = accounts[id]
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            account.amount
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromIndex == toIndex" }
        val fromLock = locks[fromId]
        val toLock = locks[toId]
        fromLock.doSafe {
            val m = accounts[fromId].amount
            check(amount <= m) { "Underflow" }
            accounts[fromId].amount -= amount
        }
        toLock.doSafe {
            val to = accounts[toId].amount
            check(!(amount > MAX_AMOUNT || to + amount > MAX_AMOUNT)) { "Overflow" }
            accounts[toId].amount += amount
        }
//        fromLock.doSafe {
//            toLock.doSafe {
//                val from = accounts[fromId]
//                val to = accounts[toId]
//                check(amount <= from.amount) { "Underflow" }
//                check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
//                from.amount -= amount
//                to.amount += amount
//            }
//        }
    }

    /**
     * Private account data structure.
     */
    class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0
    }
}