import { test as base } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

const COVERAGE_RAW = path.resolve(__dirname, '../.coverage-raw');

export const test = base.extend({
  page: async ({ page }, use) => {
    const isChromium = page.context().browser()?.browserType().name() === 'chromium';
    if (isChromium) {
      await page.coverage.startJSCoverage({ resetOnNavigation: false });
    }
    await use(page);
    if (isChromium) {
      const coverage = await page.coverage.stopJSCoverage();
      if (coverage.length > 0) {
        const id = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
        fs.mkdirSync(COVERAGE_RAW, { recursive: true });
        fs.writeFileSync(path.join(COVERAGE_RAW, `${id}.json`), JSON.stringify(coverage));
      }
    }
  },
});

export { expect } from '@playwright/test';
export type { BrowserContext } from '@playwright/test';
