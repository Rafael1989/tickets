import { expect, test } from '@playwright/test';

function uniqueUsername(): string {
  return `e2e-cust-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
}

/**
 * The one full golden-path E2E test: register -> log in -> search the
 * seeded route (see global-setup.ts) -> hold the seeded seat -> add a
 * passenger -> create the booking -> pay with the demo "approve" card ->
 * land on the booking detail page. Every step goes through the real UI,
 * the real Spring Boot backend, and real Postgres - nothing here is mocked.
 */
test('search, book, and pay for a seat end-to-end', async ({ page }) => {
  const username = uniqueUsername();

  await page.goto('/register');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Email').fill(`${username}@example.com`);
  await page.getByLabel('Password').fill('SecurePass123!');
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page).toHaveURL(/\/login$/);

  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill('SecurePass123!');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL(/\/search$/);

  await page.getByLabel('Origin').fill('E2E Origin');
  await page.getByLabel('Destination').fill('E2E Destination');

  // Assert on the search response, not only on the rendered card.
  // "element(s) not found" cannot tell apart the two things that actually go
  // wrong here: a click that landed before Angular wired the form (no request
  // at all) and a request that ran and legitimately came back empty. Waiting
  // on the response makes the failure name itself, in the plain job log,
  // without anyone having to download a trace artifact.
  const searchResponse = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/search' && response.request().method() === 'GET',
    { timeout: 15_000 },
  );
  await page.locator('button.search-btn').click();
  const response = await searchResponse;
  expect(response.status(), 'GET /api/search did not answer 200').toBe(200);

  // Deliberately not response.json(): Chromium discards small XHR bodies once
  // the renderer is done with them, and reading one here fails intermittently
  // with "No data found for resource with given identifier" - a flake in the
  // diagnostic itself, which is worse than no diagnostic. The status check
  // above already separates "no request" (this await times out) from "request
  // rejected", and the assertion below carries the third case in its message.
  const resultCard = page.locator('.result-card', { hasText: 'E2E Origin' });
  await expect(
    resultCard,
    'GET /api/search answered 200 but no card rendered for the seeded route - see the seeded rows dumped by the workflow',
  ).toBeVisible();
  await resultCard.getByRole('link', { name: 'Select seats' }).click();

  await expect(page).toHaveURL(/\/schedules\/\d+$/);
  await page.locator('button.seat').first().click();
  const continueButton = page.getByRole('button', { name: 'Continue to checkout' });
  await expect(continueButton).toBeEnabled();
  await continueButton.click();

  await expect(page).toHaveURL(/\/checkout$/);
  await page.getByLabel('Full name').fill('E2E Passenger');
  await page.getByLabel('Date of birth').fill('1990-01-01');
  await page.getByLabel('ID type').selectOption('passport');
  await page.getByLabel('ID number').fill('P1234567');
  await page.getByRole('button', { name: 'Save passenger' }).click();

  const seatPassengerSelect = page.locator('.seat-table select');
  await expect(seatPassengerSelect.locator('option', { hasText: 'E2E Passenger' })).toBeAttached();
  await seatPassengerSelect.selectOption({ label: 'E2E Passenger' });
  await page.getByRole('button', { name: 'Create booking' }).click();

  await expect(page.getByRole('heading', { name: 'Payment', exact: true })).toBeVisible();
  await page.getByLabel('Cardholder name').fill('E2E Passenger');
  await page.getByLabel('Card number').fill('4242 4242 4242 4242');
  await page.getByLabel('Expiry (MM/YY)').fill('12/29');
  await page.getByLabel('CVC').fill('123');
  await page.getByRole('button', { name: 'Pay now' }).click();

  await expect(page.getByRole('heading', { name: 'Payment successful' })).toBeVisible({ timeout: 15_000 });

  await page.getByRole('button', { name: 'View my booking' }).click();
  await expect(page).toHaveURL(/\/bookings\/\d+$/);
});
