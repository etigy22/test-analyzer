import {test,expect} from '@playwright/test';
import {readFileSync} from 'node:fs';

test('Five original GitHub scans have persisted COMPLETED and render without a partial-analysis warning',async({request,page})=>{
  const evidence=JSON.parse(readFileSync(new URL('../../docs/test-evidence/completed-static-scans/results.json',import.meta.url),'utf8').replace(/^\uFEFF/,''));
  expect(evidence.results.filter((r:any)=>r.status==='PASS').length).toBeGreaterThanOrEqual(5);
  for(const proof of evidence.results.filter((r:any)=>r.status==='PASS')) {
    const response=await request.get(`/api/scans/${proof.scanId}`);expect(response.status()).toBe(200);
    const job=await response.json();expect(job.status,proof.repository).toBe('COMPLETED');expect(job.result.truncated).toBe(false);
    expect(job.result.coverage.filter((c:any)=>c.status!=='COMPLETED' && !(c.applicable===false && c.status==='UNSUPPORTED')),proof.repository).toHaveLength(0);
  }
  await page.goto('/');await page.getByRole('navigation').getByRole('button',{name:/^Analysen/}).click();
  await page.getByRole('textbox',{name:'Quelle oder ID suchen'}).fill(evidence.results[0].scanId);
  await page.locator('.scan-name').first().click();
  await expect(page.locator('.language-strip')).toBeVisible();
  await expect(page.locator('.report-meta').getByText('Abgeschlossen',{exact:true})).toBeVisible();
  await expect(page.getByText('Eingeschränkte Analyse',{exact:true})).toHaveCount(0);
  await page.getByRole('tab',{name:/Prüfabdeckung/}).click();
  await expect(page.getByText('Außerhalb Prüfumfang',{exact:true}).first()).toBeVisible();
});
