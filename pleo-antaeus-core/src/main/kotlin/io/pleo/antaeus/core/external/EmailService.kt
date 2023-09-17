/*
    The EmailService interface provides methods to send billing-related email notifications to customers.
    Implementations of this interface are responsible for sending emails in response to
    billing success or failure events.

    Usage:
    - Use `sendBillingFailureEmail` to send an email notification for billing failure.
    - Use `sendBillingSuccessEmail` to send an email notification for successful billing.

    Note:
    - Implementations are currently not expected to throw errors during email sending.

 */
package io.pleo.antaeus.core.external

import io.pleo.antaeus.models.Invoice

interface EmailService {
    fun sendBillingFailureEmail(invoice: Invoice)
    fun sendBillingSuccessEmail(invoice: Invoice)
}