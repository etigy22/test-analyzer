import {test,expect} from '@playwright/test';
import type {APIRequestContext} from '@playwright/test';
import {zip,samples} from './fixtures';
const headers={'X-RepoGuard-Request':'true'};
let firstId='',secondId='';
async function waitJob(request:APIRequestContext,id:string){
  let job:any;await expect.poll(async()=>{const response=await request.get(`/api/scans/${id}`);job=await response.json();return ['PARTIAL','COMPLETED','FAILED','BLOCKED'].includes(job.status);},{timeout:160000,intervals:[500,1000,2000]}).toBeTruthy();return job;
}
test.describe.configure({mode:'serial'});
test('E2E-03/04/06: real ZIP scan, ten languages, filters, masked details and PDF',async({page,request})=>{
  const settings=await(await request.get('/api/settings')).json();expect(settings.malware.ready).toBe(true);
  await page.goto('/');await expect(page.getByText('API verbunden')).toBeVisible();
  await page.getByRole('navigation').getByRole('button',{name:'Neue Analyse',exact:true}).click();
  await page.getByRole('tab',{name:'ZIP-Projekt'}).click();
  await page.getByLabel('ZIP auswählen',{exact:true}).setInputFiles({name:'passive-fixtures.zip',mimeType:'application/zip',buffer:zip(samples)});
  const submitted=page.waitForResponse(r=>r.url().endsWith('/api/scans/zip')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'Analyse starten',exact:true}).click();
  const created=await(await submitted).json();firstId=created.id;expect(firstId).toBeTruthy();
  const job=await waitJob(request,firstId);expect(job.status,JSON.stringify(job)).toBe('PARTIAL');
  expect(job.result.languages.filter((x:any)=>x.name!=='Unknown')).toHaveLength(10);
  for(const language of job.result.languages.filter((x:any)=>x.name!=='Unknown')){
    expect(job.result.findings.some((f:any)=>f.language===language.name&&f.category==='SECURITY')).toBe(true);
    expect(job.result.findings.some((f:any)=>f.language===language.name&&f.category==='QUALITY')).toBe(true);
  }
  expect(JSON.stringify(job)).not.toContain('sensitive-secret-example-123456');
  expect(job.result.findings.some((f:any)=>f.ruleId==='CVE-2021-23337')).toBe(true);
  await expect(page.getByText('Eingeschränkte Analyse',{exact:true})).toBeVisible({timeout:20000});
  await page.getByLabel('Sprache',{exact:true}).selectOption('Python');
  await expect.poll(async()=>page.locator('.finding-row').count()).toBeGreaterThanOrEqual(3);
  await page.locator('.finding-row').first().click();await expect(page.getByRole('dialog')).toBeVisible();
  expect(await page.getByRole('dialog').textContent()).not.toContain('sensitive-secret-example-123456');
  await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByRole('tab',{name:/Prüfabdeckung/}).click();await expect(page.locator('.coverage-row').filter({hasText:'Nicht unterstützt'}).first()).toBeVisible();
  const pdf=await request.get(`/api/scans/${firstId}/report.pdf?locale=de`);expect(pdf.status()).toBe(200);expect(pdf.headers()['content-type']).toContain('application/pdf');
  const buffer=await pdf.body();expect(buffer.subarray(0,5).toString()).toBe('%PDF-');await test.info().attach('report.pdf',{body:buffer,contentType:'application/pdf'});
  await page.screenshot({path:'test-results/report-desktop.png',fullPage:true});
});
test('E2E-05: traversal and EICAR are blocked with no code analysis',async({request})=>{
  for(const [files,code] of [[{'../escape.py':'eval(input)'},'ZIP_PATH'],[{'eicar.txt':'X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*'},'MALWARE_DETECTED']] as const){
    const response=await request.post('/api/scans/zip',{headers,multipart:{file:{name:'negative.zip',mimeType:'application/zip',buffer:zip(files)}}});
    expect(response.status()).toBe(202);const job=await waitJob(request,(await response.json()).id);expect(job.failureCode,JSON.stringify(job)).toBe(code);expect(job.result).toBeNull();
  }
});
test('E2E-06: persisted history, comparison and deletion',async({request,page})=>{
  const response=await request.post('/api/scans/zip',{headers,multipart:{file:{name:'revised.zip',mimeType:'application/zip',buffer:zip({'fixture.py':'eval(input)\nexcept:\n    pass'})}}});
  expect(response.status()).toBe(202);secondId=(await response.json()).id;expect((await waitJob(request,secondId)).result).not.toBeNull();
  const compared=await(await request.get(`/api/scans/compare?left=${firstId}&right=${secondId}`)).json();expect(compared.unchanged.length).toBeGreaterThan(0);expect(compared.resolved.length).toBeGreaterThan(0);
  await page.goto('/');await page.getByRole('navigation').getByRole('button',{name:/^Analysen/}).click();
  await page.getByRole('checkbox',{name:`Auswählen: ${firstId}`}).check();await page.getByRole('checkbox',{name:`Auswählen: ${secondId}`}).check();await page.getByRole('button',{name:'Vergleichen (2/2)'}).click();await expect(page.getByRole('heading',{name:'Analysevergleich'})).toBeVisible();
  page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:`Löschen: ${secondId}`,exact:true}).click();await expect.poll(async()=>(await request.get(`/api/scans/${secondId}`)).status()).toBe(404);
});
test('SEC-03: hostile origins and hosts cannot mutate analyses',async({request})=>{
  const foreign=await request.post('/api/scans/github',{headers:{...headers,Origin:'https://attacker.invalid'},data:{url:'https://github.com/example/repo'}});expect(foreign.status()).toBe(403);
  const missing=await request.post('/api/scans/github',{data:{url:'https://github.com/example/repo'}});expect(missing.status()).toBe(403);
  const rebound=await request.get('/api/scans',{headers:{Host:'attacker.invalid:8088'}});expect(rebound.status()).toBe(403);
});
test('E2E-09: German/English and light/dark at 375, 768 and 1440 pixels',async({page})=>{
  await page.goto('/');await expect(page.getByText('API verbunden')).toBeVisible();
  for(const width of [375,768,1440]){
    await page.setViewportSize({width,height:950});
    for(const locale of ['de','en']){
      if(await page.locator('html').getAttribute('lang')!==locale)await page.getByRole('button',{name:locale.toUpperCase(),exact:true}).click();
      for(const theme of ['light','dark']){
        if(await page.locator('html').getAttribute('data-theme')!==theme)await page.getByRole('button',{name:locale==='de'?(theme==='dark'?'Dunkles Design':'Helles Design'):(theme==='dark'?'Dark theme':'Light theme')}).click();
        expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
        await page.screenshot({path:`test-results/dashboard-${locale}-${theme}-${width}.png`,fullPage:true});
      }
    }
  }
});
