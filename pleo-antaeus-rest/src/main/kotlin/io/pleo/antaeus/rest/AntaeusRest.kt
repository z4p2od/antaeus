/*
 * Antaeus REST API
 *
 * Roots and Endpoints:
 *
 * - /rest
 *   - GET /rest/health: Check the health of the application.
 *
 * - /rest/v1
 *   - /invoices
 *     - GET /rest/v1/invoices: Get a list of all invoices.
 *     - GET /rest/v1/invoices/{:id}: Get an invoice by ID.
 *     - GET /rest/v1/invoices/status/{:statusID}: Get invoices by status.
 *   - /customers
 *     - GET /rest/v1/customers: Get a list of all customers.
 *     - GET /rest/v1/customers/{:id}: Get a customer by ID.
 *   - /admin
 *     - POST /rest/v1/admin/autoBilling: Initiate the auto-billing process.
 *     - POST /rest/v1/admin/billByStatus/{:status}: Bill invoices by status.
 *     - POST /rest/v1/admin/billInvoice/{:id}: Bill a specific invoice.
 *     - POST /rest/v1/admin/markAsPermanentFail/{:status}: Mark invoices as permanent fail.
 *     - POST /rest/v1/admin/updateInvoiceStatus/{:id}/{:status}: Update the status of an invoice.
 */

package io.pleo.antaeus.rest

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.BadRequestResponse
import io.pleo.antaeus.core.exceptions.EntityNotFoundException
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.eclipse.jetty.http.HttpStatus



private val logger = KotlinLogging.logger {}
private val thisFile: () -> Unit = {}

class AntaeusRest(
    private val invoiceService: InvoiceService,
    private val customerService: CustomerService,
    private val billingService: BillingService
) : Runnable {

    override fun run() {
        app.start(7000)
    }

    // Set up Javalin rest app
    private val app = Javalin.create().apply {
        // InvoiceNotFoundException: return 404 HTTP status code
        exception(EntityNotFoundException::class.java) { _, ctx ->
            ctx.status(404)
        }
        // Unexpected exception: return HTTP 500
        exception(Exception::class.java) { e, _ ->
            logger.error(e) { "Internal server error" }
        }
        // On 404: return message
        error(404) { ctx -> ctx.json("not found") }
    }

    init {
        // Set up URL endpoints for the rest app
        app.routes {
            get("/") {
                it.result("Welcome to Antaeus! see AntaeusRest class for routes")
            }
            path("rest") {
                // Route to check whether the app is running
                // URL: /rest/health
                get("health") {
                    it.json("ok")
                }

                // V1
                path("v1") {
                    path("invoices") {
                        // URL: /rest/v1/invoices
                        get {
                            it.json(invoiceService.fetchAll())
                        }

                        // URL: /rest/v1/invoices/{:id}
                        get(":id") {
                            try {
                                val id = it.pathParam("id").toInt()

                                // Perform parameter validation
                                if (id <= 0) {
                                    throw NumberFormatException()
                                }
                                it.json(invoiceService.fetch(it.pathParam("id").toInt()))
                            } catch (e: NumberFormatException) {
                                it.status(HttpStatus.BAD_REQUEST_400)
                                it.result("Invalid ID format")
                            }
                        }

                        // URL: /rest/v1/invoices/status/statusID
                        get("status/:statusID") {
                            val status = it.pathParam("statusID").toUpperCase() // Convert to uppercase

                            try {
                                it.json(invoiceService.fetchByStatus(InvoiceStatus.valueOf(status)))
                            } catch (e: IllegalArgumentException) {
                                throw BadRequestResponse(
                                    "Invalid status value: $status. Valid status values are: ${
                                        InvoiceStatus.values().joinToString(", ")
                                    }"
                                )
                            }

                        }
                    }

                    path("customers") {
                        // URL: /rest/v1/customers
                        get {
                            it.json(customerService.fetchAll())
                        }

                        // URL: /rest/v1/customers/{:id}
                        get(":id") {
                            try {
                                val id = it.pathParam("id").toInt()

                                // Perform parameter validation
                                if (id <= 0) {
                                    throw NumberFormatException()
                                }
                                it.json(customerService.fetch(it.pathParam("id").toInt()))
                            } catch (e: NumberFormatException) {
                                it.status(HttpStatus.BAD_REQUEST_400)
                                it.result("Invalid ID format")
                            }
                        }
                    }

                    path("admin") {

                        // URL: /rest/v1/admin/autoBilling
                        post("autoBilling") {
                            GlobalScope.launch {
                                billingService.autoBilling()
                            }
                            it.status(HttpStatus.NO_CONTENT_204)
                        }


                        // URL: /rest/v1/admin/billByStatus/{:status}
                        post("billByStatus/:status") {
                            val status = it.pathParam("status").toUpperCase() // Convert to uppercase

                            try {
                                val invoiceStatus = InvoiceStatus.valueOf(status)

                                if (invoiceStatus == InvoiceStatus.PAID) {
                                    throw BadRequestResponse("Invalid status value: $status. 'PAID' cannot be processed.")
                                } else {
                                    // Perform the billing operation here
                                    GlobalScope.launch {
                                        billingService.billInvoices(invoiceStatus)
                                        it.status(HttpStatus.NO_CONTENT_204)
                                    }
                                    it.status(204) // No Content
                                }
                            } catch (e: IllegalArgumentException) {
                                throw BadRequestResponse(
                                    "Invalid status value: $status. Valid status values are: ${
                                        InvoiceStatus.values().filter { it != InvoiceStatus.PAID }.joinToString(", ")
                                    }"
                                )
                            }
                        }

                        // URL: /rest/v1/admin/billInvoice/{:id}
                        post("billInvoice/:id") {
                            try {
                                val id = it.pathParam("id").toInt()

                                if (id <= 0) {
                                    throw NumberFormatException()
                                }

                                val invoice = invoiceService.fetch(id)
                                GlobalScope.launch {
                                    billingService.tryToChargeInvoice(invoice)
                                    it.status(HttpStatus.NO_CONTENT_204)
                                }
                            } catch (e: NumberFormatException) {
                                it.status(HttpStatus.BAD_REQUEST_400)
                                it.result("Invalid ID format")
                            }
                        }

                        // URL: /rest/v1/admin/markAsPermanentFail/{:Status}
                        post("markAsPermanentFail/:status") {
                            val status = it.pathParam("status").toUpperCase() // Convert to uppercase

                            try {
                                val invoiceStatus = InvoiceStatus.valueOf(status)

                                if (invoiceStatus == InvoiceStatus.PAID || invoiceStatus == InvoiceStatus.PENDING) {
                                    throw BadRequestResponse("Invalid status value: $status. cannot be marked as permanent fail.")

                                } else if (invoiceStatus == InvoiceStatus.PERMANENT_FAIL) {
                                    throw BadRequestResponse("Invoices already marked as permanent fail.")
                                } else {
                                    // Perform the operation here
                                    billingService.markInvoicesAsPermanentFailed(invoiceStatus)
                                    it.status(204) // No Content
                                }
                            } catch (e: IllegalArgumentException) {
                                throw BadRequestResponse("Invalid status value: $status. Valid status values are: ${
                                    InvoiceStatus.values()
                                        .filter { it != InvoiceStatus.PAID && it != InvoiceStatus.PERMANENT_FAIL && it != InvoiceStatus.PENDING }
                                        .joinToString(", ")
                                }")
                            }
                        }

                        // URL: /rest/v1/admin/updateInvoiceStatus/{:id}/{:status}
                        post("updateInvoiceStatus/:id/:status") {
                            try {
                                val id = it.pathParam("id").toInt()
                                if (id <= 0) {
                                    throw NumberFormatException()
                                }
                                val newStatus = InvoiceStatus.valueOf(it.pathParam("status"))

                                // Call your service to update the invoice status
                                invoiceService.updateStatus(id, newStatus)

                                it.status(HttpStatus.NO_CONTENT_204)
                            } catch (e: NumberFormatException) {
                                it.status(HttpStatus.BAD_REQUEST_400)
                                it.result("Invalid invoice ID format")
                            } catch (e: IllegalArgumentException) {
                                it.status(HttpStatus.BAD_REQUEST_400)
                                it.result("Invalid invoice status")
                            }
                        }
                    }
                }
            }
        }
    }
}






