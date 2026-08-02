import { expect, test } from '@playwright/test';

function uniqueUsername(): string {
  return `e2e-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
}

test.describe('authentication', () => {
  test('register, then log in, lands on the search page', async ({ page }) => {
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
  });

  test('an unauthenticated visitor opening a guarded route is redirected to login', async ({ page }) => {
    await page.goto('/account');

    await expect(page).toHaveURL(/\/login/);
  });

  test('logging in with the wrong password shows an error toast and stays on the login page', async ({ page }) => {
    const username = uniqueUsername();
    await page.goto('/register');
    await page.getByLabel('Username').fill(username);
    await page.getByLabel('Email').fill(`${username}@example.com`);
    await page.getByLabel('Password').fill('SecurePass123!');
    await page.getByRole('button', { name: 'Register' }).click();
    await expect(page).toHaveURL(/\/login$/);

    await page.getByLabel('Username').fill(username);
    await page.getByLabel('Password').fill('WrongPassword!');
    await page.getByRole('button', { name: 'Log in' }).click();

    // errorInterceptor deliberately shows this same generic message for
    // every 401 (session-expiry and a failed login alike) rather than the
    // backend's specific "Invalid username or password" - see
    // error.interceptor.ts and its own unit test for that design choice.
    await expect(page.locator('.toast.error')).toContainText('Your session has expired');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('registering the same username twice is rejected on the second attempt', async ({ page }) => {
    const username = uniqueUsername();

    for (let attempt = 0; attempt < 2; attempt++) {
      await page.goto('/register');
      await page.getByLabel('Username').fill(username);
      await page.getByLabel('Email').fill(`${username}-${attempt}@example.com`);
      await page.getByLabel('Password').fill('SecurePass123!');
      await page.getByRole('button', { name: 'Register' }).click();
    }

    await expect(page).toHaveURL(/\/register$/);
    await expect(page.locator('.toast.error')).toBeVisible();
  });
});
