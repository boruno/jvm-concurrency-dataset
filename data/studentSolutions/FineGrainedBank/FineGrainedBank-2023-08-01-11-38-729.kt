@file:Suppress("DuplicatedCode")

package day1

import day1.Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    override fun getAmount(id: Int): Long {
        val account = accounts[id]
        account.lock.lock()
        try {
            return account.amount
        }
        finally {
            account.lock.unlock()
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        account.lock.lock()
        try {
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            return account.amount
        }
        finally {
            account.lock.unlock()
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        account.lock.lock()
        try {
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            return account.amount
        }
        finally {
            account.lock.unlock()
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        var fromIdd = fromId
        var toIdd = toId
        var amountt = amount
        if (fromId > toId) {
            fromIdd = toId
            toIdd = fromIdd
            amountt = -amount
        }

        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }
        val from = accounts[fromIdd]
        val to = accounts[toIdd]

        from.lock.lock()
        try {
            check(amountt <= from.amount) { "Underflow" }
            from.amount -= amountt
        }
        finally {
            from.lock.unlock()
        }

        to.lock.lock()
        try
        {
            check(!(amountt > MAX_AMOUNT || to.amount + amountt > MAX_AMOUNT)) { "Overflow" }
            to.amount += amountt
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

        /**
         * TODO: use this mutex to protect the account data.
         */
        val lock = ReentrantLock()
    }
}