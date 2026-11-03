import {test,expect} from '@playwright/test';
import {zip} from './fixtures';

test('History: 27 failed/blocked/result rows selectable, select all, cancel and one bulk confirmation',async({page})=>{
  const jobs=Array.from({length:27},(_,i)=>({id:`00000000-0000-4000-8000-${String(i+1).padStart(12,'0')}`,sourceType:'ZIP',source:`bulk-ui-fixture-${i}`,status:['FAILED','BLOCKED','PARTIAL'][i%3],createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),completedAt:new Date().toISOString(),failureCode:null,failureMessage:null,result:null}));
  let deleted:string[]=[],calls=0;
  await page.route('**/api/scans',route=>route.fulfill({json:jobs.filter(job=>!deleted.includes(job.id))}));
  await page.route('**/api/scans/delete',async route=>{calls++;deleted=route.request().postDataJSON().ids;await route.fulfill({status:204});});
  await page.goto('/');await page.getByRole('navigation').getByRole('button',{name:/^Analysen/}).click();
  for(const job of jobs.slice(0,3))await page.getByRole('checkbox',{name:`Auswählen: ${job.id}`,exact:true}).check();
  await expect(page.getByText('3 ausgewählt',{exact:true})).toBeVisible();
  await page.getByRole('checkbox',{name:'Alle sichtbaren auswählen',exact:true}).check();
  await expect(page.getByRole('checkbox',{checked:true})).toHaveCount(28);
  await page.setViewportSize({width:375,height:950});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  page.once('dialog',dialog=>dialog.dismiss());await page.getByRole('button',{name:'Auswahl löschen (27)',exact:true}).click();
  expect(calls).toBe(0);await expect(page.getByText('27 ausgewählt',{exact:true})).toBeVisible();
  page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'Auswahl löschen (27)',exact:true}).click();
  await expect.poll(()=>calls).toBe(1);expect(deleted).toHaveLength(27);
  await expect(page.getByRole('checkbox',{name:/^Auswählen:/})).toHaveCount(0);
});

test('History/API: failed jobs bulk delete atomically, active job protects the entire selection',async({page,request})=>{
  test.setTimeout(240000);
  const headers={'X-RepoGuard-Request':'true'}, ids:string[]=[];
  const create=async()=>{const response=await request.post('/api/scans/zip',{headers,multipart:{file:{name:'bulk-delete-test.zip',mimeType:'application/zip',buffer:zip({'../bulk-delete-marker.txt':'passive fixture'})}}});expect(response.status()).toBe(202);const job=await response.json();ids.push(job.id);return job.id;};
  const wait=async(id:string)=>{await expect.poll(async()=>Boolean((await(await request.get(`/api/scans/${id}`)).json()).completedAt),{timeout:90000,intervals:[1000,2000]}).toBe(true);};
  try {
    const first=await create();await wait(first);const second=await create();await wait(second);
    const active=await create();
    const refused=await request.post('/api/scans/delete',{headers,data:{ids:[first,active]}});expect(refused.status()).toBe(409);
    expect((await request.get(`/api/scans/${first}`)).status()).toBe(200);await wait(active);
    await page.goto('/');await page.getByRole('navigation').getByRole('button',{name:/^Analysen/}).click();
    for(const id of [first,second]){await page.getByRole('textbox',{name:'Quelle oder ID suchen'}).fill(id);await page.getByRole('checkbox',{name:`Auswählen: ${id}`,exact:true}).check();}
    await expect(page.getByRole('button',{name:'Vergleichen (2/2)',exact:true})).toBeDisabled();
    page.once('dialog',dialog=>dialog.accept());const deleted=page.waitForResponse(r=>r.url().endsWith('/api/scans/delete')&&r.request().method()==='POST');
    await page.getByRole('button',{name:'Auswahl löschen (2)',exact:true}).click();expect((await deleted).status()).toBe(204);
    for(const id of [first,second])expect((await request.get(`/api/scans/${id}`)).status()).toBe(404);
  } finally {for(const id of ids)await request.delete(`/api/scans/${id}`,{headers});}
});
