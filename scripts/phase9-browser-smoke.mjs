import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { chromium } from '../extension/node_modules/playwright/index.mjs'

const root=new URL('../',import.meta.url)
const envText=await readFile(new URL('.env',root),'utf8')
function envValue(name){const line=envText.split(/\r?\n/).filter(v=>v.trim().startsWith(`${name}=`)).at(-1);if(!line)throw new Error(`Missing local environment value: ${name}`);return line.slice(line.indexOf('=')+1).trim().replace(/^(['"])(.*)\1$/,'$2')}
const password=`P9!${createHash('sha256').update(`phase9-smoke|${envValue('JWT_SECRET')}`).digest('hex').slice(0,30).toUpperCase()}z`
const errors=[],warnings=[]
const browser=await chromium.launch({channel:'msedge',headless:true})
try{
 const page=await browser.newPage({viewport:{width:1600,height:1100}})
 page.on('pageerror',e=>errors.push(`pageerror:${e.message}`))
 page.on('console',m=>{if(m.type()==='error')errors.push(`console:${m.text()} @ ${m.location().url||'unknown'}`);if(m.type()==='warning')warnings.push(m.text())})
 await page.goto('http://127.0.0.1:5173/login',{waitUntil:'networkidle'})
 await page.locator('input[autocomplete="username"]').fill('phase9_smoke')
 await page.locator('input[autocomplete="current-password"]').fill(password)
 await page.getByRole('button',{name:'进入 JobPilot'}).click();await page.waitForURL('**/dashboard')
 await page.getByRole('heading',{name:'Offer 与截止事项'}).waitFor()
 const dashboard=await page.locator('main.main-area').innerText();for(const value of ['Offer 与截止事项','进行中','待办截止','完整 Analytics'])if(!dashboard.includes(value))throw new Error(`Dashboard missing ${value}`)
 await page.getByRole('link',{name:/Offer Center/}).first().click();await page.waitForURL('**/offers');await page.getByRole('heading',{name:'Offer Center'}).waitFor()
 await page.getByText('Phase 9 Alpha',{exact:false}).first().waitFor();const offerText=await page.locator('main.main-area').innerText();for(const value of ['保证年现金','潜在年现金','截止与历史','对比快照','Decision boundary'])if(!offerText.includes(value))throw new Error(`Offer Center missing ${value}`)
 await page.getByRole('link',{name:/Analytics/}).first().click();await page.waitForURL('**/analytics');await page.getByRole('heading',{name:'Analytics'}).waitFor()
 await page.getByText('真实转化漏斗').waitFor();const analytics=await page.locator('main.main-area').innerText();for(const value of ['Discovered','PLATFORM','JOB_DIRECTION','MATCH_SCORE_BUCKET','数据导出与删除','快照历史'])if(!analytics.includes(value))throw new Error(`Analytics missing ${value}`)
 if(errors.length)throw new Error(`Browser emitted ${errors.length} error(s): ${errors.join(' | ')}`)
 process.stdout.write(JSON.stringify({status:'PASS',browser:'Microsoft Edge',login:'PASS',dashboard:'PASS',offerCenter:'PASS',analytics:'PASS',privacyControls:'PASS',consoleErrors:0,consoleWarnings:warnings.length}))
}finally{await browser.close()}
