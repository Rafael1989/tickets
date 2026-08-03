-- Run once by the postgres entrypoint, on first initialisation of an empty
-- data volume only. POSTGRES_DB in docker-compose.yml already created
-- `ticketwave` (dev); these are the other two the project needs.
--
-- Liquibase builds the schema inside each of them on first application/test
-- run — it does not create the databases themselves, which is why this file
-- exists.

-- Target of the *IT suite (application-test.yml). Deliberately a different
-- name from the dev database so a forgotten env var can never point a test
-- run at dev data.
CREATE DATABASE ticketwave_test;

-- Target of the Playwright e2e suite. Kept separate because e2e/global-setup.ts
-- TRUNCATEs every table it finds — pointing it at either database above would
-- destroy dev data or race the *IT suite.
CREATE DATABASE ticketwave_e2e;
