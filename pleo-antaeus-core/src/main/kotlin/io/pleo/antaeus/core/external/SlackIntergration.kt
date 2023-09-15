package io.pleo.antaeus.core.external

import io.pleo.antaeus.models.Invoice

interface SlackIntegration {
    fun sendChargingFailureMessage(invoice: Invoice, errorMessage: String)
    fun sendMarkedPermanentFailMessage(invoice: Invoice)
}