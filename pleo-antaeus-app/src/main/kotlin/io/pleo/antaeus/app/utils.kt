
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.EmailService
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.external.SlackIntegration
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.*
import mu.KotlinLogging
import java.math.BigDecimal
import kotlin.random.Random

// This will create all schemas and setup initial data
internal fun setupInitialData(dal: AntaeusDal) {
    val customers = (1..100).mapNotNull {
        dal.createCustomer(
            currency = Currency.values()[Random.nextInt(0, Currency.values().size)]
        )
    }

    customers.forEach { customer ->
        (1..10).forEach {
            dal.createInvoice(
                amount = Money(
                    value = BigDecimal(Random.nextDouble(10.0, 500.0)),
                    currency = customer.currency
                ),
                customer = customer,
                status = if (it == 1) InvoiceStatus.PENDING else InvoiceStatus.PAID
            )
        }
    }
}

// This is the mocked instance of the payment provider
internal fun getPaymentProvider(): PaymentProvider {
    return object : PaymentProvider {
        override fun charge(invoice: Invoice): Boolean {
            return Random.nextBoolean()
        }
    }
}

// This is the mocked instance of the email service
internal fun getEmailService(): EmailService {
    return object : EmailService {

        private val emailLogger = KotlinLogging.logger {}
        override fun sendBillingFailureEmail(invoice: Invoice) {
            // log the email sending event
            emailLogger.info { "Sending billing failure email to ${invoice.customerId} for invoice ${invoice.id}" }
        }

        override fun sendBillingSuccessEmail(invoice: Invoice) {
            // log the email sending event
            emailLogger.info { "Sending billing successful email to ${invoice.customerId} for invoice ${invoice.id}" }
        }
    }
}

// This is the mocked instance of the slack integration
internal fun getSlackIntegration(): SlackIntegration {
    return object : SlackIntegration {
        private val slackLogger = KotlinLogging.logger {}
        override fun sendChargingFailureMessage(invoice: Invoice, errorMessage: String) {
            // log the Slack message event
            slackLogger.info { "Sending Slack message to support channel: Billing failed for invoice ${invoice.id}. Error: $errorMessage" }
        }

        override fun sendMarkedPermanentFailMessage(invoice: Invoice) {
            // log the Slack message event
            slackLogger.info {
                "Sending Slack message message to support channel: Invoice ${invoice.id} is now marked as permanent failed reason: ${invoice.status}"
            }
        }
    }
}


