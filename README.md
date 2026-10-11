# RouteAssign — Configurable Delivery Partner Assignment Engine

RouteAssign is a backend project built with **Java, Spring Boot, MySQL, and Redis**. It explores how delivery partners can be selected using configurable business rules and a weighted scoring model.

The goal is to keep assignment policies separate from the core assignment workflow, so supported business-rule changes can be made through configuration rather than by rewriting the entire engine.

> **Project status:** This is a development project and an exploration of a reusable assignment service. It is not presented as a production-ready replacement for existing delivery platforms.

## Key Features

- **Eligibility checks before scoring:** Mandatory constraints are evaluated before candidates are ranked.
- **Weighted assignment scoring:** The current scoring configuration uses the following factors:
  - Distance — 40%
  - Capacity — 20%
  - Rating — 15%
  - Idle time — 15%
  - Same-vendor preference — 10%
- **Strategy Pattern:** Scoring factors are separated into strategies and combined by a central scoring engine.
- **Database-configurable policies:** Supported business rules and scoring weights are stored in MySQL.
- **Redis caching:** Frequently accessed assignment configuration can be cached in Redis, with MySQL as the source of truth.
- **Assignment lifecycle and reassignment:** APIs support assignment-related workflows.
- **Postman API collection:** An end-to-end collection is included to help exercise API workflows.

## Technology Stack

- Java
- Spring Boot
- Spring Data JPA / Hibernate
- MySQL
- Redis
- REST APIs
- Postman

> Confirm the exact versions and dependencies from the project's build file before adding version badges or version claims here.

## Architecture Overview

At a high level, the assignment workflow is:

```text
API Request
    |
    v
Eligibility Checks
    |
    v
Assignment Scoring Engine
    |
    +--> Distance Strategy
    +--> Capacity Strategy
    +--> Rating Strategy
    +--> Idle-Time Strategy
    +--> Same-Vendor Strategy
    |
    v
Rank Eligible Partners
    |
    v
Persist Assignment Decision (MySQL)
```

MySQL stores persistent application data and configurable business rules. Redis is used as a cache for frequently accessed assignment configuration.

## Configurable Rules and Extensibility

The scoring weights and supported business rules can be adjusted through configuration. For example, a business can change the relative importance of distance and idle time without changing the central scoring workflow.

When a genuinely new scoring algorithm is needed, a corresponding strategy implementation may still be required. The architecture aims to make that extension localized rather than requiring changes throughout the assignment engine.

Idle time is one factor intended to account for how long a partner has been waiting. It should not be interpreted as a guarantee of fair outcomes; fairness depends on the complete eligibility, scoring, and assignment policies.

## Quick-Commerce Direction

The same general assignment concepts may be useful for traditional e-commerce and quick-commerce systems, but those operating models have different requirements.

A quick-commerce adaptation may need additional domain behavior for fulfillment-store selection, inventory reservation, picking time, delivery SLAs, and rider availability. These capabilities should not be assumed to be implemented merely by changing scoring weights.

## Getting Started

The exact setup depends on the project source code and your local environment. Use the following as a checklist and confirm values against `pom.xml` or `build.gradle` and `application.properties`.

### Prerequisites

- A compatible JDK for the project
- Maven or Gradle, depending on the build file
- MySQL
- Redis
- Postman (for the API collection)

### 1. Clone the repository

```bash
git clone <YOUR_PUBLIC_GITHUB_REPOSITORY_URL>
cd <YOUR_REPOSITORY_DIRECTORY>
```

Replace the placeholders with your repository URL and directory name.

### 2. Configure MySQL

Create a development database and a database user with appropriate permissions. Configure the datasource in your local application configuration.

Do not commit real database credentials. Prefer environment variables or a local, ignored configuration file.

### 3. Configure Redis

Start Redis and configure the Redis host, port, and password as appropriate for your environment. Keep Redis protected and do not publish its password.

### 4. Configure application properties

Review `src/main/resources/application.properties` (or the configuration file used by the project). Set the MySQL and Redis connection details using local values or environment variables.

Never commit real secrets, JWT signing keys, passwords, or access tokens.

### 5. Run the application

For a Maven project, the command is typically:

```bash
./mvnw spring-boot:run
```

On Windows, if the Maven wrapper is present:

```powershell
.\mvnw.cmd spring-boot:run
```

If the repository does not include a Maven wrapper, use the build tool and instructions defined by the project.

### 6. Verify the application

If Spring Boot Actuator health is enabled and exposed, check:

```text
http://localhost:8080/actuator/health
```

A healthy application commonly responds with:

```json
{
  "status": "UP"
}
```

The endpoint may differ if the application uses another port or Actuator configuration.

## Postman API Testing

The public Postman collection is intended to help test the API workflows.

1. Import the JSON collection from the `postman/` directory.
2. Set the collection variable `baseUrl` to your running backend URL, for example `http://localhost:8080`.
3. Review any test-only variables, including `testPassword`, and use values suitable for your local environment.
4. Run the initialization phase only against a disposable development database.
5. After initialization succeeds and generated IDs are captured, run the order/assignment phase.
6. Inspect responses and test results; not every request has been verified against every environment.

**Important:** Initialization requests create persistent test records. Running them repeatedly may cause duplicate-data conflicts. Some phase-two requests may create orders or alter assignment state. Do not run the collection against production data.

## Fairness and Assignment Decisions

RouteAssign considers idle time alongside distance, capacity, rating, and same-vendor preference. This is a design choice intended to account for partners who have waited longer, but it does not by itself guarantee fair distribution.

Possible future evaluation areas include assignment distribution over time, starvation prevention, workload balancing, and the effect of changing scoring weights.

## Security and Data Safety

- Do not commit `.env` files, database passwords, Redis passwords, JWT secrets, tokens, or personal data.
- Use test accounts and synthetic data in the Postman collection.
- Review every request and script before publishing the collection publicly.
- Any database-reset SQL should be treated as destructive and run only against a disposable development database.
- Do not expose administrative or diagnostic endpoints publicly without appropriate access controls.

## Possible Next Steps

- Add automated unit and integration tests for scoring strategies and eligibility rules.
- Test edge cases and concurrent order/stock operations.
- Improve assignment decision observability and structured logging.
- Explore tenant-specific configuration and authorization if evolving toward a multi-tenant service.
- Explore quick-commerce-specific workflows such as fulfillment selection, picking time, inventory reservation, and delivery SLAs.

## Repository Structure

Adjust this example to match the actual repository layout:

```text
RouteAssign/
├── src/
├── postman/
│   └── RouteAssign_Public_API_Collection.json
├── README.md
└── .gitignore
```

## Contributing

Suggestions, issues, and discussions about assignment strategies, configurable rules, testing, and system design are welcome. Please open an issue to discuss a substantial change before submitting a pull request.

## Disclaimer

RouteAssign is a learning and portfolio project exploring configurable delivery-partner assignment. References to e-commerce and quick commerce describe potential directions, not claims of integration with or endorsement by any existing company.
