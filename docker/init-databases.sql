-- Run once by the postgres entrypoint, on first initialisation of an empty
-- data volume only. POSTGRES_DB in docker-compose.yml already created
-- `ticketwave` (dev); these are the other two the project needs.
--
-- Liquibase builds the schema inside each of them on first application/test
-- run — it does not create the databases themselves, which is why this file
-- exists.

-- Target of the Playwright e2e suite. Kept separate from the dev database
-- because e2e/global-setup.ts TRUNCATEs every table it finds.
CREATE DATABASE ticketwave_e2e;

-- Note there is no ticketwave_test here: the *IT suite runs against its own
-- Testcontainers instance and needs nothing from this file.
