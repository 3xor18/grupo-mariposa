import { expect, type Locator, type Page } from '@playwright/test';
import { copy } from './copy';
import { environment, requirePassword } from './environment';

const keycloakAuthPath = /\/realms\/mariposa\/protocol\/openid-connect\/auth/;

export function visible(page: Page, content: string | RegExp): Locator {
  return page.getByText(content).or(page.getByLabel(content)).first();
}

export async function openLoginScreen(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByRole('button', { name: copy.login })).toBeVisible();
}

export async function login(page: Page): Promise<void> {
  await openLoginScreen(page);
  await page.getByRole('button', { name: copy.login }).click();
  await page.waitForURL(keycloakAuthPath);
  await page.locator('#username').fill(environment.username);
  await page.locator('#password').fill(requirePassword());
  await page.locator('#kc-login').click();
  await page.waitForURL((url) => url.origin === new URL(environment.baseUrl).origin);
  await expect(page.getByRole('button', { name: copy.logout })).toBeVisible();
}

export async function searchOrder(page: Page, orderId: string): Promise<void> {
  const field = page.getByRole('textbox', { name: copy.orderIdField });
  await field.click();
  await page.keyboard.type(orderId);
  await page.keyboard.press('Enter');
}

export async function openOrdersTab(page: Page): Promise<void> {
  const name = copy.ordersTab;
  const tab = page
    .getByRole('tab', { name })
    .or(page.getByRole('button', { name }))
    .first();
  await tab.click();
}
