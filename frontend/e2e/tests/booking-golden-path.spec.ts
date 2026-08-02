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
  await page.locator('button.search-btn').click();

  const resultCard = page.locator('.result-card', { hasText: 'E2E Origin' });
  await expect(resultCard).toBeVisible();
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
