/*
    The SlackIntegration interface defines methods for sending notifications to a Slack channel
    in response to billing-related events. Implementations are responsible for delivering
    messages to Slack, such as charging failure notifications and marking invoices as permanent fail.

    Usage:
    - Use `sendChargingFailureMessage` to send a Slack message for charging failures.
    - Use `sendMarkedPermanentFailMessage` to send a Slack message when invoices are marked as permanent fail.

    Note:
    - Implementations are currently not expected to throw errors during Slack message delivery.

 */


package io.pleo.antaeus.core.external

import io.pleo.antaeus.models.Invoice

interface SlackIntegration {
    fun sendChargingFailureMessage(invoice: Invoice, errorMessage: String)
    fun sendMarkedPermanentFailMessage(invoice: Invoice)
}