const {test}=require('node:test');
const assert=require('node:assert/strict');
const {once}=require('node:events');
const http=require('node:http');
const {createRequire}=require('node:module');
const nestRequire=createRequire(require.resolve('@nestjs/platform-express'));
const multer=nestRequire('multer');
const express=require('express');
const {createApp}=require('../apps/api/dist/main');
const {digest}=require('../apps/api/dist/security');

test('patched multipart parser accepts bytes and rejects oversized, invalid and aborted uploads',async t=>{
 assert.equal(nestRequire('multer/package.json').version,'2.5.0');
 const app=express();let filterStarted,releaseFilter;
 const started=new Promise(resolve=>{filterStarted=resolve;});
 const upload=multer({storage:multer.memoryStorage(),limits:{fileSize:1024},fileFilter:(req,file,cb)=>{
  if(req.headers['x-test-abort']){releaseFilter=()=>cb(null,true);filterStarted();}
  else cb(null,file.mimetype==='application/pdf');
 }});
 app.post('/upload',upload.single('file'),(req,res)=>req.file?res.json({bytes:req.file.buffer.toString(),size:req.file.size}):res.sendStatus(415));
 app.use((err,req,res,next)=>res.status(err.code==='LIMIT_FILE_SIZE'?413:400).json({error:'Upload rejected'}));
 const server=app.listen(0,'127.0.0.1');await once(server,'listening');
 t.after(()=>new Promise(resolve=>server.close(resolve)));
 const port=server.address().port;
 async function send(data,type='application/pdf'){
  const body=new FormData();body.append('file',new Blob([data],{type}),'synthetic.pdf');
  return fetch(`http://127.0.0.1:${port}/upload`,{method:'POST',body});
 }
 const good=await send('%PDF-synthetic');assert.equal(good.status,200);assert.equal((await good.json()).bytes,'%PDF-synthetic');
 assert.equal((await send('x'.repeat(1025))).status,413);
 assert.equal((await send('not-a-pdf','text/plain')).status,415);
 const malformed=await fetch(`http://127.0.0.1:${port}/upload`,{method:'POST',headers:{'Content-Type':'multipart/form-data; boundary=bad'},body:'--bad\r\ninvalid'});
 assert.equal(malformed.status,400);
 // Reproduce a pending asynchronous fileFilter followed by transport teardown.
 const req=http.request({host:'127.0.0.1',port,path:'/upload',method:'POST',headers:{'x-test-abort':'1','content-type':'multipart/form-data; boundary=abort'} });
 req.on('error',()=>{});
 req.write('--abort\r\nContent-Disposition: form-data; name="file"; filename="test.pdf"\r\nContent-Type: application/pdf\r\n\r\npayload');
 await started;req.destroy();await new Promise(resolve=>setTimeout(resolve,50));releaseFilter();
 await new Promise(resolve=>setTimeout(resolve,50));
 assert.equal((await send('%PDF-after-abort')).status,200);
});

test('actual HRMS upload URL route signs tenant-scoped S3 uploads and enforces validation/authentication',async t=>{
 Object.assign(process.env,{NODE_ENV:'production',APP_ORIGIN:'https://hrms.example.invalid',TRUST_PROXY:'172.30.10.2',REDIS_URL:'redis://unused.invalid',S3_BUCKET:'synthetic-upload-test',S3_REGION:'ap-south-1',AWS_ACCESS_KEY_ID:'SYNTHETIC_TEST_ONLY',AWS_SECRET_ACCESS_KEY:'SYNTHETIC_TEST_ONLY',AWS_EC2_METADATA_DISABLED:'true'});
 const token='synthetic-session';let record;
 const db={session:{findUnique:async({where})=>where.id===digest(token)?{userId:'user',expiresAt:new Date(Date.now()+100000),user:{active:true}}:null},membership:{findUnique:async({where})=>where.userId_companyId.companyId==='company-a'?{role:'EMPLOYEE',employee:{id:'employee'}}:null},storedFile:{create:async({data})=>{record=data;return {id:'file',...data};}}};
 const app=await createApp(db);await app.listen(0,'127.0.0.1');t.after(()=>app.close());const base=await app.getUrl();
 const headers={Origin:process.env.APP_ORIGIN,'Content-Type':'application/json',Cookie:`aster_session=${token}`,'x-company-id':'company-a'};
 const send=(body,extra={})=>fetch(base+'/api/files/upload-url',{method:'POST',headers:{...headers,...extra},body:JSON.stringify(body)});
 const response=await send({name:'payslip.pdf',contentType:'application/pdf'});assert.equal(response.status,201);
 const {url}=await response.json();const signed=new URL(url);
 assert.equal(signed.hostname,'synthetic-upload-test.s3.ap-south-1.amazonaws.com');
 assert.ok(signed.pathname.startsWith('/company-a/'));assert.equal(signed.searchParams.get('X-Amz-Expires'),'120');
 assert.ok(signed.searchParams.has('X-Amz-Signature'));assert.equal(record.ownerId,'user');assert.equal(record.companyId,'company-a');
 assert.equal((await send({name:'bad.exe',contentType:'application/x-msdownload'})).status,400);
 assert.equal((await send({name:'file.pdf',contentType:'application/pdf'},{Cookie:''})).status,401);
 assert.equal((await send({name:'file.pdf',contentType:'application/pdf'},{'x-company-id':'foreign'})).status,403);
 delete process.env.S3_BUCKET;assert.equal((await send({name:'file.pdf',contentType:'application/pdf'})).status,503);
});
