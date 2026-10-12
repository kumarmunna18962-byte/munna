const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = process.cwd();
const port = 4173;
const types = {
  '.html':'text/html; charset=utf-8',
  '.webmanifest':'application/manifest+json',
  '.svg':'image/svg+xml',
  '.js':'text/javascript; charset=utf-8',
  '.css':'text/css; charset=utf-8',
  '.txt':'text/plain; charset=utf-8'
};
const files = new Map([
  ['/dc-calculator/index.html','dc-calculator/index.html'],
  ['/dc-calculator/manifest.webmanifest','dc-calculator/manifest.webmanifest'],
  ['/dc-calculator/dc-icon.svg','dc-calculator/dc-icon.svg'],
  ['/dc-calculator/sw.js','dc-calculator/sw.js'],
  ['/dc-calculator/privacy-policy.html','dc-calculator/privacy-policy.html']
]);
const server = http.createServer((req,res)=>{
  const rel=files.get(new URL(req.url,'http://localhost').pathname);
  if(!rel){res.writeHead(404);res.end('not found');return;}
  const file=path.join(root,rel);
  res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store'});
  fs.createReadStream(file).pipe(res);
});
const checks=[];
const check=(name,condition,detail='')=>{assert.ok(condition,detail||name);checks.push(name);};
(async()=>{
  await new Promise(resolve=>server.listen(port,'127.0.0.1',resolve));
  const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
  const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block',acceptDownloads:true});
  const page=await context.newPage();
  const errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  page.on('console',m=>{if(m.type()==='error')errors.push('console: '+m.text());});
  await page.route('https://uxrbxpykdxhxvlrwelzo.supabase.co/functions/v1/dc-calculator-vip',async route=>{
    const req=route.request();
    const headers={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Methods':'POST, OPTIONS','Access-Control-Allow-Headers':'content-type, apikey, x-admin-token','Content-Type':'application/json'};
    if(req.method()==='OPTIONS'){await route.fulfill({status:200,headers,body:'ok'});return;}
    let body={};try{body=req.postDataJSON();}catch{}
    let out={active:true,plan:'monthly',expires_at:new Date(Date.now()+25*86400000).toISOString()};
    if(body.action==='admin_list')out={requests:[]};
    if(body.action==='submit')out={ok:true,status:'pending',request_id:'00000000-0000-4000-8000-000000000001'};
    if(body.action==='approve')out={ok:true,approved:true,expires_at:new Date(Date.now()+30*86400000).toISOString()};
    if(body.action==='reject')out={ok:true,rejected:true};
    await route.fulfill({status:200,headers,body:JSON.stringify(out)});
  });
  await page.goto('http://127.0.0.1:'+port+'/dc-calculator/index.html');
  await page.waitForFunction(()=>document.querySelector('#answer')?.textContent==='0');
  check('calculator renders');
  check('old app icon still linked',await page.locator('link[rel="icon"]').getAttribute('href')==='./dc-icon.svg');
  check('VIP plans show requested prices',(await page.locator('.vip-plans').innerText()).includes('₹99')&&(await page.locator('.vip-plans').innerText()).includes('₹1,099'));
  const math=await page.evaluate(()=>{
    const p=window.DCCalculatorTest.parse;
    const cases=[['200+10%',220],['100-10%',90],['25%*80',20],['sqrt(81)',9],['cbrt(27)',3],['sin(30)',0.5],['cos(60)',0.5],['tan(45)',1],['asin(0.5)',30],['log(1000)',3],['ln(e)',1],['2^3^2',512],['(2+3)(4)',20],['2pi',2*Math.PI],['5!',120],['3+4*2',11],['2^-2',0.25],['abs(-7)',7],['exp(0)',1],['(3+2)%',0.05]];
    const bad=[];
    for(const [expr,expected] of cases){const actual=p(expr);if(Math.abs(actual-expected)>1e-8*Math.max(1,Math.abs(expected)))bad.push({expr,expected,actual});}
    window.DCCalculatorTest.setAngleMode('RAD');
    const rad=p('sin(pi/2)');
    if(Math.abs(rad-1)>1e-9)bad.push({expr:'sin(pi/2) radians',expected:1,actual:rad});
    window.DCCalculatorTest.setAngleMode('DEG');
    return {bad,total:cases.length+1};
  });
  check('21 scientific and percentage math cases pass',math.bad.length===0,JSON.stringify(math.bad));
  // Test the visible keypad interaction.
  await page.locator('#keypad [data-num="2"]').click();
  await page.locator('#keypad [data-op="+"]').click();
  await page.locator('#keypad [data-num="2"]').click();
  await page.locator('#keypad [data-action="equals"]').click();
  check('keypad calculates 2 + 2', (await page.locator('#answer').innerText()).trim()==='4');
  // Student and pricing tools.
  await page.locator('#studentTools').click();
  await page.locator('#toolsOverlay').waitFor({state:'visible'});
  await page.locator('#marksGot').fill('420');
  await page.locator('#marksTotal').fill('500');
  await page.locator('#calcMarks').click();
  check('student percentage tool returns 84%',(await page.locator('#marksResult').innerText()).includes('84%'));
  await page.locator('[data-tool-tab="money"]').click();
  await page.locator('#priceOriginal').fill('999');
  await page.locator('#discountRate').fill('10');
  await page.locator('#gstRate').fill('18');
  await page.locator('#calcMoney').click();
  check('GST and discount tool calculates',(await page.locator('#moneyResult').innerText()).includes('1,060.94'));
  await page.locator('[data-tool-tab="emi"]').click();
  await page.locator('#loanAmount').fill('12000');
  await page.locator('#loanRate').fill('0');
  await page.locator('#loanMonths').fill('12');
  await page.locator('#calcEmi').click();
  check('zero-interest EMI calculates',(await page.locator('#emiResult').innerText()).includes('₹1,000.00'));
  await page.locator('[data-tool-tab="convert"]').click();
  await page.locator('#convertValue').fill('1');
  await page.locator('#calcConvert').click();
  check('unit converter calculates km to miles',(await page.locator('#convertResult').innerText()).includes('0.621371'));
  // VIP backend is mocked in this browser test. This verifies UI wiring, not a real paid membership.
  await page.locator('#closeTools').click();
  await page.locator('#vipBtn').click();
  await page.waitForFunction(()=>document.querySelector('#vipExtras')?.hidden===false);
  check('VIP extras open only after server status says active');
  await page.locator('#openVipVault').click();
  await page.locator('#vaultPass').fill('private passphrase 2026');
  await page.locator('#vaultPassConfirm').fill('private passphrase 2026');
  await page.locator('#vaultUnlock').click();
  await page.locator('#vaultContents').waitFor({state:'visible'});
  check('vault creates encrypted storage');
  await page.locator('#vaultFiles').setInputFiles({name:'private-test.txt',mimeType:'text/plain',buffer:Buffer.from('DC private vault test payload')});
  await page.getByText('private-test.txt',{exact:true}).waitFor({state:'visible'});
  const stored=await page.evaluate(async()=>{
    const db=await new Promise((resolve,reject)=>{const q=indexedDB.open('dc-calculator-vault-v1',1);q.onsuccess=()=>resolve(q.result);q.onerror=()=>reject(q.error);});
    const list=await new Promise((resolve,reject)=>{const t=db.transaction('entries','readonly'),q=t.objectStore('entries').getAll();q.onsuccess=()=>resolve(q.result);q.onerror=()=>reject(q.error);});
    const r=list[0],raw=new TextDecoder().decode(new Uint8Array(r.ciphertext));
    return {count:list.length,hasPlainName:Object.hasOwn(r,'name')||Object.hasOwn(r,'type'),hasPlainText:raw.includes('DC private vault test payload')||raw.includes('private-test.txt')};
  });
  check('vault stores encrypted bytes without plaintext names/content',stored.count===1&&!stored.hasPlainName&&!stored.hasPlainText);
  await page.locator('#vaultLock').click();
  check('vault lock hides decrypted files',await page.locator('#vaultContents').isHidden());
  await page.locator('#vaultPass').fill('wrong passphrase');
  await page.locator('#vaultUnlock').click();
  await page.waitForFunction(()=>document.querySelector('#vaultError')?.textContent.includes('Incorrect passphrase'));
  check('wrong vault passphrase is rejected');
  await page.locator('#vaultPass').fill('private passphrase 2026');
  await page.locator('#vaultUnlock').click();
  await page.getByText('private-test.txt',{exact:true}).waitFor({state:'visible'});
  const downloadPromise=page.waitForEvent('download');
  await page.locator('.vault-item-actions button').first().click();
  const download=await downloadPromise;
  const tmp=path.join(require('node:os').tmpdir(),download.suggestedFilename());
  await download.saveAs(tmp);
  check('vault file decrypts and downloads correctly',fs.readFileSync(tmp,'utf8')==='DC private vault test payload');
  fs.unlinkSync(tmp);
  page.once('dialog',d=>d.accept());
  await page.locator('.vault-item-actions button').last().click();
  await page.getByText('private-test.txt',{exact:true}).waitFor({state:'hidden'});
  check('vault file can be deleted');
  await page.locator('#vaultBack').click();
  await page.locator('#closeVip').click();
  // Small-phone overflow regression.
  await page.setViewportSize({width:360,height:800});
  const overflow=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}));
  check('small mobile layout has no horizontal overflow',overflow.scroll<=overflow.width,JSON.stringify(overflow));
  check('no uncaught JavaScript/console errors',errors.length===0,errors.join('\n'));
  console.log(JSON.stringify({passed:checks.length,total:checks.length,checks},null,2));
  await browser.close();
  await new Promise(resolve=>server.close(resolve));
})().catch(async error=>{
  console.error(error.stack||String(error));
  server.close(()=>process.exit(1));
  process.exitCode=1;
});
