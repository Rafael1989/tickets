import { defineConfig, devices } from '@playwright/test';

const FRONTEND_PORT = 4201;

/**
 * Phase 3 of the IT roadmap: real browser against the real Spring Boot
 * backend against real local Postgres (ticketwave_e2e - never the dev
 * database, see global-setup.ts). fullyParallel is off and there is a
 * single worker on purpose: every spec shares the one seeded schedule/seat
 * fixture, and concurrent workers would race each other for it.
 *
 * No `webServer` entry here: Node's child_process spawn for shell commands
 * (mvn.cmd/npm.cmd) needs cmd.exe, and cmd.exe spawning is blocked in this
 * sandboxed shell environment. The backend (mvn -o spring-boot:run,
 * SERVER_PORT=8081, DB_NAME=ticketwave_e2e) and frontend (npm start) are
 * started manually as separate background processes before running this
 * suite - see the E2E run notes for the exact env vars each needs.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  globalSetup: './global-setup.ts',
  use: {
    baseURL: `http://localhost:${FRONTEND_PORT}`,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
