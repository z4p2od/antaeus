package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.InvoiceStatus
import java.lang.Exception
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) {
    fun billInvoices() {
        val pendingInvoices = invoiceService.fetchPending()
        logger.info { "start processing ${pendingInvoices.size} invoices" }

        pendingInvoices.forEach { invoice ->
            val isInvoicePaid = try {
                paymentProvider.charge(invoice)
            } catch (e: Exception) {
                invoiceService.updateStatus(invoice.id, InvoiceStatus.FAILED)
                return@forEach
            }
            if (isInvoicePaid) {
                invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID)
                 }
            }
        }
    }

