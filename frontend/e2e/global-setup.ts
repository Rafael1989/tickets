import { Client } from 'pg';

/**
 * DATABASE SAFETY: this must point at the dedicated ticketwave_e2e database
 * (created once, manually, outside of this script - Liquibase can create
 * tables but not the database itself) and must match the DB_NAME passed to
 * the backend's webServer entry in playwright.config.ts. Never point this at
 * the "ticketwave" dev database or "ticketwave_test" (used by the backend's
 * *IT suite) - both would be destructively truncated on every E2E run.
 */
const DB_CONFIG = {
  host: 'localhost',
  port: 5432,
  database: 'ticketwave_e2e',
  user: 'postgres',
  password: 'root',
};

/**
 * Playwright starts the webServer entries (and waits for their health-check
 * URLs) before running globalSetup, so by the time this runs the backend
 * has already booted and Liquibase has already created the schema against
 * ticketwave_e2e. This poll is just defensive insurance against that
 * ordering assumption rather than the primary wait mechanism.
 */
async function waitForSchema(client: Client): Promise<void> {
  for (let attempt = 0; attempt < 60; attempt++) {
    const { rows } = await client.query<{ exists: string | null }>(
      "SELECT to_regclass('public.seats') AS exists",
    );
    if (rows[0].exists) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error('Timed out waiting for Liquibase to create the E2E schema (public.seats never appeared).');
}

export default async function globalSetup(): Promise<void> {
  const client = new Client(DB_CONFIG);
  await client.connect();
  try {
    await waitForSchema(client);

    // Truncates every app table except Liquibase's own bookkeeping tables,
    // discovered dynamically so this never needs updating as new features
    // add new tables. RESTART IDENTITY keeps generated ids small and
    // predictable across repeated E2E runs.
    await client.query(`
      DO $$
      DECLARE
        tbl text;
      BEGIN
        FOR tbl IN
          SELECT tablename FROM pg_tables
          WHERE schemaname = 'public' AND tablename NOT IN ('databasechangelog', 'databasechangeloglock')
        LOOP
          EXECUTE format('TRUNCATE TABLE %I RESTART IDENTITY CASCADE', tbl);
        END LOOP;
      END $$;
    `);

    const {
      rows: [operator],
    } = await client.query<{ user_id: number }>(
      `INSERT INTO users (username, password_hash, email, role) VALUES ($1, $2, $3, 'operator') RETURNING user_id`,
      ['e2e-operator', 'unused-seed-hash-never-logged-in-with', 'e2e-operator@example.com'],
    );
    const {
      rows: [route],
    } = await client.query<{ route_id: number }>(
      `INSERT INTO routes (operator_id, type, origin, destination, duration_minutes)
       VALUES ($1, 'bus', $2, $3, 180) RETURNING route_id`,
      [operator.user_id, 'E2E Origin City', 'E2E Destination City'],
    );
    const {
      rows: [schedule],
    } = await client.query<{ schedule_id: number }>(
      `INSERT INTO schedules (route_id, departure_time, arrival_time, base_fare, currency, status)
       VALUES ($1, now() + interval '10 days', now() + interval '10 days' + interval '3 hours', 25.00, 'USD', 'scheduled')
       RETURNING schedule_id`,
      [route.route_id],
    );
    await client.query(
      `INSERT INTO seats (schedule_id, seat_number, class, status, price_modifier)
       VALUES ($1, '1A', 'economy', 'available', 1.000)`,
      [schedule.schedule_id],
    );
  } finally {
    await client.end();
  }
}
