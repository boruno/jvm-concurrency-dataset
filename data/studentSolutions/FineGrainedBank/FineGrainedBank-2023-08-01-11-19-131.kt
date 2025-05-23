@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }
    private val accountLocks: Array<Lock> = Array(accountsNumber) { ReentrantLock() }

    fun <T> withFineLock(accountId: Int, body: () -> T): T {
        accountLocks[accountId].lock()
        return try {
            body()
        } finally {
            accountLocks[accountId].unlock()
        }
    }

    override fun getAmount(id: Int): Long = withFineLock(id) {
        val account = accounts[id]
        account.amount
    }

    override fun deposit(id: Int, amount: Long): Long = withFineLock(id) {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
        account.amount += amount
        account.amount
    }

    override fun withdraw(id: Int, amount: Long): Long = withFineLock(id) {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        check(account.amount - amount >= 0) { "Underflow" }
        account.amount -= amount
        account.amount
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        withFineLock(fromId) {
            withFineLock(toId) {
                require(amount > 0) { "Invalid amount: $amount" }
                require(fromId != toId) { "fromId == toId" }
                val from = accounts[fromId]
                val to = accounts[toId]
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

        /**
         * TODO: use this mutex to protect the account data.
         */
        val lock = ReentrantLock()
    }
}