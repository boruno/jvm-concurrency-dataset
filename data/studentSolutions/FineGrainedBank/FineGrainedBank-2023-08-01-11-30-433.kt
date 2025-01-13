@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    override fun getAmount(id: Int): Long {
        val account = accounts[id]
        account.lock.lock()
        val amount = try {
            account.amount
        }
        finally {
            account.lock.unlock()
        }
        return amount
    }

    override fun deposit(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        account.lock.lock()
        val newAmount = try {
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            account.amount
        }
        finally {
            account.lock.unlock()
        }
        return newAmount
    }

    override fun withdraw(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        account.lock.lock()
        val newAmount = try {
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            account.amount
        }
        finally {
            account.lock.unlock()
        }
        return newAmount
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }
        val from = accounts[fromId]

        from.lock.lock()
        try {
            check(amount <= from.amount) { "Underflow" }
            from.amount -= amount
        }
        finally {
            from.lock.unlock()
        }

        val to = accounts[toId]
        to.lock.lock()
        try {
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            to.amount += amount
        }
        finally {
            to.lock.unlock()
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

        val lock = ReentrantLock()
    }
}