const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const errors = [];
  page.on('console', m => { if (m.type()==='error') errors.push('CONSOLE: '+m.text()); });
  page.on('pageerror', e => errors.push('PAGEERROR: '+e.message));
  const reqs = [];
  page.on('response', r => { if (r.url().includes('/api/')) reqs.push(r.request().method()+' '+r.url().replace(/.*\/api/,'/api')+' -> '+r.status()); });
  await page.goto('http://frontend:3000/login', {waitUntil:'networkidle'});
  await page.fill('input[formcontrolname="username"], input[name="username"]', 'student1').catch(()=>{});
  // generic fill
  const inputs = await page.$$('input');
  if(inputs[0]) await inputs[0].fill('student1');
  if(inputs[1]) await inputs[1].fill('Student@12345678');
  await page.click('button[type="submit"], button:has-text("Sign In")').catch(()=>{});
  await page.waitForTimeout(2500);
  await page.goto('http://frontend:3000/sessions/new', {waitUntil:'networkidle'});
  await page.waitForTimeout(3000);
  console.log('URL after new session:', page.url());
  // click Save & Complete
  await page.click('button:has-text("Save & Complete")').catch(e=>errors.push('CLICK_FAIL: '+e.message));
  await page.waitForTimeout(2500);
  console.log('URL after Save&Complete:', page.url());
  console.log('--- API calls ---'); reqs.forEach(r=>console.log(r));
  console.log('--- errors ---'); errors.forEach(e=>console.log(e));
  await browser.close();
})();
