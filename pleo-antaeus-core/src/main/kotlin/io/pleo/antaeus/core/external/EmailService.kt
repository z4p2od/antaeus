package io.pleo.antaeus.core.external

import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.Customer

interface EmailService {
    fun sendPaymentFailureEmail(invoice: Invoice)
}