import * as fs from 'fs';
import * as path from 'path';

const COVERAGE_RAW = path.resolve(__dirname, '../.coverage-raw');
const COVERAGE_OUT = path.resolve(__dirname, '../coverage');

export default async function globalTeardown() {
  if (!fs.existsSync(COVERAGE_RAW)) return;

  const files = fs.readdirSync(COVERAGE_RAW).filter((f) => f.endsWith('.json'));
  if (files.length === 0) return;

  let totalFunctions = 0;
  let coveredFunctions = 0;

  for (const file of files) {
    const entries: any[] = JSON.parse(
      fs.readFileSync(path.join(COVERAGE_RAW, file), 'utf-8')
    );
    for (const entry of entries) {
      if (!entry.url?.includes('localhost')) continue;
      for (const fn of entry.functions ?? []) {
        totalFunctions++;
        if (fn.ranges?.some((r: any) => r.count > 0)) {
          coveredFunctions++;
        }
      }
    }
  }

  fs.mkdirSync(COVERAGE_OUT, { recursive: true });

  const pct = totalFunctions > 0
    ? Math.round((coveredFunctions / totalFunctions) * 100)
    : 0;

  const summary = {
    total: {
      functions: { total: totalFunctions, covered: coveredFunctions, pct },
      statements: { total: totalFunctions, covered: coveredFunctions, pct },
    },
  };

  fs.writeFileSync(
    path.join(COVERAGE_OUT, 'coverage-summary.json'),
    JSON.stringify(summary, null, 2)
  );

  fs.rmSync(COVERAGE_RAW, { recursive: true, force: true });
}
