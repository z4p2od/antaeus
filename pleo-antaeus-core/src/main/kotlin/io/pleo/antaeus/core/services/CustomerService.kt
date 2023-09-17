/*
 * The `CustomerService` class provides operations for managing customers:
 *
 * - `fetchAll()`: Fetches all customers from the database.
 * - `fetch(id: Int)`: Fetches a specific customer by its ID or throws a `CustomerNotFoundException`.
 *
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Customer

class CustomerService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Customer> {
        return dal.fetchCustomers()
    }

    fun fetch(id: Int): Customer {
        return dal.fetchCustomer(id) ?: throw CustomerNotFoundException(id)
    }
}
