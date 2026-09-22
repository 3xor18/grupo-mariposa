import { expect, test } from '@playwright/test';
import { login, openLoginScreen, openOrdersTab, searchOrder, visible } from '../support/app';
import { copy, seed } from '../support/copy';

test.describe('order tracker smoke', () => {
  test('serves an installable PWA shell', async ({ page, request }) => {
    const manifest = await request.get('/manifest.json');
    expect(manifest.ok()).toBeTruthy();
    expect(await manifest.json()).toMatchObject({ name: 'Mariposa Order Tracker' });
    await openLoginScreen(page);
  });

  test.describe('authenticated', () => {
    test.beforeEach(async ({ page }) => {
      await login(page);
    });

    test('finds an approved order with its totals', async ({ page }) => {
      await searchOrder(page, seed.approvedOrderId);
      await expect(visible(page, copy.approved)).toBeVisible();
      await expect(visible(page, seed.approvedGrandTotal)).toBeVisible();
    });

    test('shows the empty state for an unknown order', async ({ page }) => {
      await searchOrder(page, seed.unknownOrderId);
      await expect(visible(page, copy.notFound)).toBeVisible();
    });

    test('renders the recent orders screen', async ({ page }) => {
      await openOrdersTab(page);
      await expect(visible(page, copy.recentOrders)).toBeVisible();
    });
  });
});
