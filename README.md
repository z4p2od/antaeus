> :warning: This repository was archived automatically since no ownership was defined :warning:
>
> For details on how to claim stewardship of this repository see:
>
> [How to configure a service in OpsLevel](https://www.notion.so/pleo/How-to-configure-a-service-in-OpsLevel-f6483fcb4fdd4dcc9fc32b7dfe14c262)
>
> To learn more about the automatic process for stewardship which archived this repository see:
>
> [Automatic process for stewardship](https://www.notion.so/pleo/Automatic-process-for-stewardship-43d9def9bc9a4010aba27144ef31e0f2)

## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

Open the project using your favorite text editor. If you are using IntelliJ, you can open the `build.gradle.kts` file and it is gonna setup the project in the IDE for you.

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```

*Running through docker*

Install docker for your platform

```
docker build -t antaeus
docker run antaeus
```

### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── buildSrc
|  | gradle build scripts and project wide dependency declarations
|  └ src/main/kotlin/utils.kt 
|      Dependencies
|
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database 
|       models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the Internal and API models used throughout the
|       application.
|
└── pleo-antaeus-rest
        Entry point for HTTP REST API. This is where the routes are defined.
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!

# Initial Considerations

The primary focus of the application is to handle invoice payments efficiently. Here are the key points related to the scope:

## Timing of Payments

The exact timing of charging invoices is not critical. The primary objective is to ensure that invoices are eventually paid, without concerns about immediate billing.

## Avoiding Double Charges

A critical concern is to prevent double-charging customers. This means ensuring that customers are not billed twice for the same invoice.

## Handling Payment Failures

Payment failures can occur for various reasons, and the response to these failures varies:

### Retriable Failures without Human Intervention (e.g., Network Errors/Exceptions)

- These failures can be automatically retried without the need for human intervention.
- The system can wait and retry the payment until it succeeds.

### Failures Requiring Internal Actions before Retry (e.g., Customer Not Found, Currency Mismatch)

- These failures necessitate actions either by a human or the application before it makes sense to retry the payment.
- For example, if a customer is not found or there's a currency mismatch, actions should be taken to address these issues internally before retrying the payment.

### Failures Due to Customer Insufficient Balance

- Payments fail because the customer's account lacks sufficient balance.
- In such cases, the customer must be notified, and steps should be taken to ensure they can cover the payment.
- This may involve notifying the customer and giving them an opportunity to increase their account balance.

These initial considerations provided the foundation for designing my solution. It's essential to have different handling mechanisms for each type of failure to ensure smooth and reliable invoice processing while maintaining customer trust and minimizing disruptions.

# Solution Overview
 This solution aims to provide an efficient and flexible billing automation system that minimizes disruptions, ensures reliable payments, and allows for easy customization and maintenance.

## Scheduling

### Cron Jobs vs. Scheduling Logic

- For scheduling payments, I decided to use cron jobs that would call api endpoints at certain time intervals as it seemed fairly simple and customisable approach.  I initially considered using multiple cron jobs scheduled to run at different intervals. One would handle the charging of pending invoices once a month, while the others would manage the retries of failed invoices. However, I later realized that I could simplify the process by incorporating scheduling logic directly into my billing service, thus needing only one cronjob.

### Flexible and Customizable Scheduling Logic

- To achieve this, I developed a scheduling logic inside the billing service that determines which invoices to charge based on the current date. This approach offers flexibility and ease of customization. I also included a configuration file that sets the billing schedule by day of the week and defines network retry strategies.

## Handling Payment Failures

### Network Errors

- For network errors, I implemented an exponential backoff retry strategy with a cap on the number of retries. If the invoices still can't be charged, we mark them as failed due to a network error and try again the next day.

### Customer Not Found and Currency Mismatch

- For exceptions such as "customer not found" and "currency mismatch," as well as unknown errors, I assumed that these issues would be handled by humans using API endpoints we created. To facilitate this, I integrated a mock external Slack integration that informs an internal channel when these errors occur. We retry these errors once a week.

### Failed Payments Due to Insufficient Funds

- In the case of failed payments due to insufficient funds, there's little that can be done internally other than notifying the customer. To address this, I incorporated a mock external email service that notifies the customer about the event, giving them an opportunity to resolve the issue. We retry these payments on a later date.

## Preventing Duplicate Charges

- To ensure that invoices are not charged twice, I made the assumption that the payment provider is idempotent. In other words, it should not charge the same invoice twice and should throw an exception if such an attempt is made.

## Concurrency 

- Billing invoices is an operation that can be done concurrently if the right measures exist to prevent double-charging invoices. Therefore, I decided to implement concurrency using coroutines, considering it as an opportunity to learn and explore their usage. However, this decision introduced a fair amount of complexity and challenges (as detailed in the "Challenges" section).


## Admin Path

- In addition to the endpoint for automated billing, I decided to create an "admin path" that allows access to the full range of billing service functionality. Admins have the capability to trigger billing and retries of invoices on demand. They can also update individual invoice statuses and mark invoices as permanently failed.

## Testing

- The solution was tested manually by calling the exposed endpoints via postman. Using coroutines introduced issues with unit testing that unfortunately I wasn't able to resolve on time. However,  I've added some commented out tests, to illustrated how I'd have tested my code more extensively given the time.

# Billing Service and REST API Structure

## REST API Endpoints

The REST API provides the following endpoints for managing billing and other operations:

### Health Check

- **GET /rest/health:** Check the health of the application.

### Invoices

- **GET /rest/v1/invoices:** Get a list of all invoices.
- **GET /rest/v1/invoices/{:id}:** Get an invoice by ID.
- **GET /rest/v1/invoices/status/{:statusID}:** Get invoices by status.

### Customers

- **GET /rest/v1/customers:** Get a list of all customers.
- **GET /rest/v1/customers/{:id}:** Get a customer by ID.

### Admin (Billing Operations)

- **POST /rest/v1/admin/autoBilling:** Initiate the auto-billing process.
- **POST /rest/v1/admin/billByStatus/{:status}:** Bill invoices by status.
- **POST /rest/v1/admin/billInvoice/{:id}:** Bill a specific invoice.
- **POST /rest/v1/admin/markAsPermanentFail/{:status}:** Mark invoices as permanent fail.
- **POST /rest/v1/admin/updateInvoiceStatus/{:id}/{:status}:** Update the status of an invoice.

## Billing Service

The `BillingService` class is responsible for automating the billing process. It handles various billing-related operations, including charging invoices, updating invoice statuses, sending notifications, and handling exceptions. Here are some key details:

### Parameters

- **paymentProvider:** Responsible for processing invoice charges.
- **invoiceService:** Manages invoice data and statuses.
- **emailService:** Sends email notifications to customers related to billing.
- **slackIntegration:** Integrates with Slack for error notifications.
- **billingConfiguration:** Configures billing schedules and retry strategies.

### Functions

- **autoBilling():** Initiates automated billing, including processing pending invoices and retrying failed ones.
- **billInvoices(invoiceStatus: InvoiceStatus):** Attempts to bill invoices with the specified status concurrently.
- **markInvoicesAsPermanentFailed(invoiceStatus: InvoiceStatus):** Marks invoices as permanent failures.
- **tryToChargeInvoice(invoice: Invoice):** Tries to bill a given invoice, handling exceptions and retries.

## Billing Service Configuration

The `BillingConfiguration` class configures billing schedules and retry logic. Key settings include:

- **maxRetries:** Maximum number of retries for network and unknown errors.
- **permanentFailStatuses:** Invoice statuses marked as permanent failures.
- **statusesToBill:** Mapping of days of the week to the statuses to process on those days.
- **getStatusesToBill():** Determines invoices to bill on a specific day.
- **shouldBillPendingInvoices:** Checks if pending invoices should be billed (e.g., on the first day of the month).
- **shouldMarkAsPermanentFailed:** Checks if invoices should be marked as permanent failures (e.g., on the last day of the month).
- **delayFromRetryStrategy:** Calculates delay before the next retry based on a retry strategy.

## Running Automated Billing

To schedule and run automated billing, a cron job is set up to trigger the autoBilling endpoint. Here's an example of a cron job that executes a POST request:

```cron
# Cron job to execute a POST request to trigger autoBilling
0 0 * * * curl --location --request POST 'http://anteaus:7000/rest/v1/admin/autoBilling' >> /var/log/cron.log 2>&1
```

# Future Improvements

While the current implementation of the billing automation system is functional and reliable, there are several areas where it can be further enhanced and expanded. Here are some ideas for future improvements:

## 1. Increase Test Coverage

Expanding the test coverage is essential for ensuring the robustness and correctness of the system. Comprehensive unit tests, integration tests, and end-to-end tests should be developed to cover various scenarios and edge cases. This will help maintain the stability and reliability of the billing automation system.

## 2. Introduce Database Metadata

Consider extending the database schema to include metadata related to invoice processing. This could include tracking the number of failed retry attempts for each invoice and timestamping important events. With this enhanced metadata, monitoring the system's performance becomes more insightful. It also enables the implementation of more flexible retry and permanent failure strategies. Currently, retries are grouped by status, and permanent failures are determined solely based on a day of the month.

## 3. Automated Currency Handling

If it can be assumed that currency information is consistently accurate either on the customer or invoice side, consider automating the handling of currency mismatch exceptions. This could involve integrating with an external currency conversion service to automatically convert currencies when necessary. This enhancement would streamline the billing process and reduce the need for manual intervention in cases of currency discrepancies.

## 4. Global Network Retry Control

When network errors occur, the system currently retries multiple invoices concurrently. In situations where network issues affect all invoices simultaneously, consider introducing a global network retry control mechanism. This mechanism would prevent parallel retries during network error events, ensuring that the system does not overwhelm external services with simultaneous retry attempts. This enhancement can help manage network resources more effectively and prevent potential disruptions caused by excessive retry attempts.

These future improvements offer opportunities to enhance the system's efficiency, flexibility, and reliability, ultimately providing a more robust and user-friendly billing automation solution for the organization.

## 5. Failsafes for Idempotency in Concurrent Charging

While the assumption that the payment provider is idempotent is a reasonable one, introducing additional failsafes can provide an extra layer of assurance against unintentional double charging of invoices, especially when invoices are processed concurrently. A couple of options could be Idempotency Tokens or Distributed Locking


# Challenges

## Learning Curve
- For the past decade, I haven't had much programming experience. During my university years, my focus was primarily on C, assembly, and some Haskell. So, in essence, everything I encountered in this project was entirely new to me. Git, Kotlin (including functional programming concepts), Gradle, Docker, and even tools like Postman required time to get familiar with. 

## Outdated Dependencies

-One particularly annoying challenge was the project's reliance on outdated versions of Gradle, Kotlin, and various dependencies. This issue became evident when I attempted to integrate Coroutines into the project and especially during unit testing. Seems that older versions lacked several features present in the latest stable releases. Unfortunately, I couldn't bring the project up to date within the constraints of the project timeline.  

## Cron Job Setup
- Setting up the cron job to run with docker was also challenging and to be honest still some parts of the docker files I understand only on a high level, but I guess if it works 🤷😅
 
## Coroutines Complexity 
- Coroutines, while promising, introduced complexity to the project. At my current skill level, I possess only a high-level understanding of concepts such as coroutine scope and dispatcher strategies. Testing Coroutines proved to be particularly challenging, and debugging issues within this context posed a significant hurdle. I believe that resolving these challenges could become more feasible once the project's environment and dependencies are updated to more recent versions.

# Final Thoughts

Looking back I think I learned quite a lot in these last three weeks, but I also realise there's so much more to learn. I think I managed to progress quite fast, due to my solid theoretical computer science foundation as well as by utilizing AI tools as a teacher. AI also helped me with my ADHD as I was able to find solutions to my problems fast without getting discouraged. I also found it quite funny that Despite my typically unstructured disposition and resistance to following rigid rules, I found myself embracing the concept of clean coding. 

All in all, I really enjoyed this challenge and think it was a great learning experience for me and to be honest I kinda feel proud with what I managed to achieve, if I think back to where I started.  

Thanks for reviewing it and I hope you like it 😁


