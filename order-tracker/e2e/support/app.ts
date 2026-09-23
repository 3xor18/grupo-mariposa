import { expect, type Page } from '@playwright/test';
import { copy } from './copy';
import { environment, requirePassword } from './environment';

function keycloakAuthorizePath(): RegExp {
  return new RegExp(`/realms/${environment.realm}/protocol/openid-connect/auth`);
}

export async function openLoginScreen(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByRole('button', { name: copy.login, exact: true })).toBeVisible();
}

export async function login(page: Page): Promise<void> {
  await openLoginScreen(page);
  await page.getByRole('button', { name: copy.login, exact: true }).click();
  await page.waitForURL(keycloakAuthorizePath());
  await page.locator('#username').fill(environment.username);
  await page.locator('#password').fill(requirePassword());
  await page.locator('#kc-login').click();
  await page.waitForURL((url) => url.origin === new URL(environment.baseUrl).origin);
  await expect(page.getByRole('button', { name: copy.logout, exact: true })).toBeVisible();
}

export async function searchOrder(page: Page, orderId: string): Promise<void> {
  const field = page.getByRole('textbox', { name: copy.orderIdField, exact: true });
  await field.click();
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.type(orderId);
  await page.keyboard.press('Enter');
}

export async function openOrdersTab(page: Page): Promise<void> {
  const name = copy.ordersTab;
  await page.getByRole('tab', { name }).or(page.getByRole('button', { name })).click();
}
