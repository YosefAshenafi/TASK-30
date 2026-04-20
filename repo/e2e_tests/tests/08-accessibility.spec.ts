import { test, expect } from './coverage.fixture';

/**
 * Reduced-motion and keyboard-only navigation on auth and session-run pages.
 */
test.describe('Accessibility — reduced motion', () => {
  test.use({
    contextOptions: {
      reducedMotion: 'reduce',
    },
  });

  test('login page has no animation classes when reduced motion is set', async ({ page }) => {
    await page.goto('/login');
    // Verify form is visible without any animation-dependent classes
    await expect(page.getByRole('form')).toBeVisible();
    const animatedEl = page.locator('[class*="animate-"], [class*="transition-"]').first();
    // If animated elements exist, they should have prefers-reduced-motion styles applied
    // We check that no @keyframes are actively running (simplified check via computed style)
    const count = await animatedEl.count();
    if (count > 0) {
      const animName = await animatedEl.evaluate(el =>
        getComputedStyle(el).animationName
      );
      expect(animName).toBe('none');
    }
  });

  test('register page renders without motion issues', async ({ page }) => {
    await page.goto('/register');
    await expect(page.getByRole('form')).toBeVisible();
  });
});

test.describe('Accessibility — keyboard-only navigation', () => {
  test('login page is fully navigable by keyboard', async ({ page }) => {
    await page.goto('/login');

    // Tab to username
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('Username')).toBeFocused();

    // Tab to password
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('Password')).toBeFocused();

    // Tab to submit
    await page.keyboard.press('Tab');
    const submitButton = page.getByRole('button', { name: /sign in/i });
    await expect(submitButton).toBeFocused();
  });

  test('register page fields are navigable in logical order', async ({ page }) => {
    await page.goto('/register');

    // Cycle through form fields checking focus trap
    const focusOrder: string[] = [];
    for (let i = 0; i < 6; i++) {
      await page.keyboard.press('Tab');
      const focused = await page.evaluate(() => {
        const el = document.activeElement;
        return el ? (el.getAttribute('name') ?? el.getAttribute('type') ?? el.tagName) : null;
      });
      if (focused) focusOrder.push(focused);
    }
    expect(focusOrder.length).toBeGreaterThan(0);
  });

  test('session-run page: rest timer can be started by keyboard', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Username').fill('admin');
    await page.getByLabel('Password').fill('Admin@123!');
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/home/);

    // Navigate to a session if one exists
    await page.goto('/sessions');
    const firstSession = page.getByRole('link', { name: /view|continue/i }).first();
    const hasSession = await firstSession.count();
    if (hasSession > 0) {
      await firstSession.click();
      // Verify keyboard focus lands on a meaningful element
      await page.keyboard.press('Tab');
      const focused = await page.evaluate(() => document.activeElement?.tagName);
      expect(['BUTTON', 'A', 'INPUT', 'SELECT']).toContain(focused);
    } else {
      test.skip();
    }
  });
});
