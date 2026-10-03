# Akkha Wallet Pay

A Java 17 / Spring Boot wallet application with a Thymeleaf web interface, MySQL persistence, email OTP flows, and a **simulated** payment gateway.

## Important: demo software

This project is for learning and demonstrations. Wallet top-ups and service payments use a local mock gateway. They do not process real card payments or transfer real money. Do not collect real card numbers/CVV or use this application to hold real funds. The add-funds screen accepts card-like values for the simulation only and does not save the card number, expiry, or CVV.

The project has not been reviewed or hardened for production financial use. Before a public deployment, review authentication/session security, CSRF protections, rate limits, database migrations/backups, privacy requirements, monitoring, and provider integrations.

## Requirements

- Java 17 or newer
- Maven 3.8+
- MySQL 8 (or compatible MySQL server)

## Run locally

1. Create a MySQL database named `banking_wallet`.
2. Copy `.env.example` to `.env` and set database credentials. Set `MAIL_USERNAME` and `MAIL_PASSWORD` only if you need email OTP delivery. For Gmail, use a Google App Password, not your regular Google password.
3. Export those values in your shell (Spring Boot does not automatically load `.env`) or configure them in your IDE run configuration.
4. Start the app:

   ```sh
   mvn spring-boot:run
   ```

5. Open `http://localhost:8081`.

Alternatively, build and run the executable jar:

```sh
mvn -DskipTests package
java -jar target/DigitalWalletSystem-0.0.1-SNAPSHOT.jar
```

## Configuration

Configure these environment variables in your hosting provider's secret/environment settings. Do not commit real values to Git.

| Variable | Purpose |
| --- | --- |
| `PORT` | HTTP port; defaults to `8081` locally and uses the hosting provider's assigned port when set. |
| `DB_URL` | Full JDBC URL for the MySQL database, including the database name. |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL credentials. |
| `MAIL_HOST` / `MAIL_PORT` | SMTP server settings; defaults to Gmail SMTP on port 587. |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP sender credentials for registration and recovery codes. |
| `PAYWALLET_ADMIN_EMAIL` | Optional existing account email to grant the app's admin role at startup. |
| `PAYWALLET_DEMO_PROVIDER_OUTCOME` | `SUCCESS` or `FAILED` for the mock payment gateway; defaults to `SUCCESS`. |

The configured MySQL database must already exist. Hibernate updates tables on startup (`ddl-auto=update`); use explicit schema migrations before production use.

## Deploy with Docker

The included `Dockerfile` builds the jar and runs it on Java 17. Deploy this repository with a host that supports Docker, attach a reachable MySQL 8 database, and set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the host's environment settings. Add the SMTP variables to enable OTP email. The service reads `PORT` from the host. Never add production credentials to `.env.example` or source control.

## Current features

- Registration/login and email OTP verification/recovery
- Wallet dashboard, transfers, requests, transaction history, and MPIN flows
- Savings goals, finance summary, QR screens, linked-bank demo screens, and admin/merchant views
- Demo add-funds debit/credit card form and mock payment/ledger recording

Some integrations and payment flows are simulations, as called out in the UI.
