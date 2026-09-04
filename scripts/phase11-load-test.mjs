import { mkdir, writeFile } from 'node:fs/promises'
import { dirname } from 'node:path'

const baseUrl=process.env.JOBPILOT_LOAD_BASE_URL||'http://127.0.0.1:8088'
const token=process.env.JOBPILOT_LOAD_TOKEN
if(!token)throw new Error('JOBPILOT_LOAD_TOKEN is required')
const concurrency=Number(process.argv[2]||5),requests=Number(process.argv[3]||30),warmup=Number(process.argv[4]||3),output=process.argv[5]
const endpoints=[['jobList','/api/v1/jobs?limit=20'],['recommendation','/api/v1/recommendations?limit=20'],['dashboard','/api/v1/dashboard']]
const percentile=(values,p)=>{if(!values.length)return null;const sorted=[...values].sort((a,b)=>a-b);return Number(sorted[Math.min(sorted.length-1,Math.ceil(p*sorted.length)-1)].toFixed(2))}
async function request(path){const start=performance.now();try{const response=await fetch(baseUrl+path,{headers:{Authorization:`Bearer ${token}`,'X-Trace-Id':`p11-load-${crypto.randomUUID()}`}});await response.arrayBuffer();return {ok:response.ok,ms:performance.now()-start,status:response.status}}catch{return {ok:false,ms:performance.now()-start,status:0}}}
for(const [,path] of endpoints)for(let i=0;i<warmup;i++)await request(path)
const results=[]
for(const [name,path] of endpoints){const values=[];let success=0,failed=0;const started=performance.now();for(let offset=0;offset<requests;offset+=concurrency){const batch=await Promise.all(Array.from({length:Math.min(concurrency,requests-offset)},()=>request(path)));for(const item of batch){values.push(item.ms);item.ok?success++:failed++}}const seconds=(performance.now()-started)/1000;results.push({name,path,requests,success,failed,errorRate:Number((failed/requests).toFixed(4)),requestsPerSecond:Number((requests/seconds).toFixed(2)),p50Ms:percentile(values,.5),p95Ms:percentile(values,.95),p99Ms:percentile(values,.99),maxMs:Number(Math.max(...values).toFixed(2)),thresholdPassed:failed===0&&percentile(values,.95)<1000})}
const report={schemaVersion:'phase11-load-v1',createdAtUtc:new Date().toISOString(),concurrency,requestsPerEndpoint:requests,warmup,results,status:results.every(v=>v.thresholdPassed)?'PASS':'FAIL'}
if(output){await mkdir(dirname(output),{recursive:true});await writeFile(output,JSON.stringify(report,null,2),'utf8')}
console.log(JSON.stringify(report))
if(report.status!=='PASS')process.exitCode=2
