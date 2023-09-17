/*
 * The `InvoiceService` class provides operations for managing invoices:
 *
 * - `fetchAll()`: Fetches all invoices from the database.
 * - `fetchByStatus(status: InvoiceStatus)`: Fetches invoices by their status.
 * - `fetch(id: Int)`: Fetches a specific invoice by its ID or throws an `InvoiceNotFoundException`.
 * - `updateStatus(id: Int, status: InvoiceStatus)`: Updates the status of a specific invoice by its ID.
 *
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus

class InvoiceService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Invoice> {
        return dal.fetchInvoices()
    }

    fun fetchByStatus(status: InvoiceStatus): List<Invoice> {
        return dal.fetchInvoicesByStatus(status)
    }

    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun updateStatus(id: Int, status: InvoiceStatus ) {
        fetch(id) // check if id exists else InvoiceNotFoundException will be thrown by fetch(id)
        dal.updateInvoiceStatus(id, status)
    }
}
