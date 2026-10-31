const {test}=require('node:test');
const assert=require('node:assert/strict');
const {spawnSync}=require('node:child_process');
const Redis=require('ioredis');
const {createApp}=require('../apps/api/dist/main');
const {hashPassword,digest}=require('../apps/api/dist/security');
const {validateEnv,checkRedis}=require('../apps/api/dist/config');
const {bootstrapIdentity}=require('../apps/api/dist/bootstrap-config');
const origin='https://hrms.example.invalid';
const config={NODE_ENV:'production',DATABASE_URL:'postgresql://test:test@postgres/test',REDIS_URL:'redis://:test@redis:6379',APP_ORIGIN:origin,TRUST_PROXY:'172.30.10.2'};

test('environment validation rejects missing/unsafe inputs without echoing values',()=>{
 assert.doesNotThrow(()=>validateEnv(config));
 for(const name of ['NODE_ENV','DATABASE_URL','REDIS_URL','APP_ORIGIN','TRUST_PROXY']) assert.throws(()=>validateEnv({...config,[name]:''}));
 assert.throws(()=>validateEnv({...config,REDIS_URL:'redis://localhost:6379'}));
 assert.throws(()=>validateEnv({...config,APP_ORIGIN:'http://example.invalid'}));
 assert.throws(()=>validateEnv({...config,TRUST_PROXY:'true'}));
 assert.throws(()=>validateEnv({...config,ADMIN_PASSWORD:'never-log-this'}),e=>!e.message.includes('never-log-this'));
 assert.throws(()=>validateEnv(config,'worker'),/SMTP_HOST/);
});
test('bootstrap rejects demonstration identities and missing company details',()=>{
 const env={ADMIN_PASSWORD:'synthetic-test-password',ADMIN_NAME:'Operator',COMPANY_NAME:'Synthetic Company'};
 for(const email of ['admin@daksha.local','admin@example.com','admin@demo.company.com','admin@test.invalid']) assert.throws(()=>bootstrapIdentity({...env,ADMIN_EMAIL:email}));
 assert.equal(bootstrapIdentity({...env,ADMIN_EMAIL:'operator@micromap.in'}).email,'operator@micromap.in');
 assert.throws(()=>bootstrapIdentity({...env,ADMIN_EMAIL:'operator@micromap.in',COMPANY_NAME:''}));
});
test('schema push is blocked in production before Prisma executes',()=>{
 const result=spawnSync(process.execPath,['scripts/db-push.mjs'],{env:{...process.env,NODE_ENV:'production',DATABASE_URL:'postgresql://test:test@localhost/test'},encoding:'utf8'});
 assert.equal(result.status,1);assert.match(result.stderr,/restricted to local development/);
});
test('Redis availability check fails closed',async t=>{
 process.env.REDIS_URL=config.REDIS_URL;
 t.mock.method(Redis.prototype,'connect',async()=>{throw Error('synthetic connection failure');});
 await assert.rejects(checkRedis(),/Redis startup check failed/);
});
test('real HTTP pipeline enforces tenant, role, origin, cookies and throttling',async t=>{
 Object.assign(process.env,config);
 let attempts=0,mode='normal';const keys=[];const sessions=new Map();
 t.mock.method(Redis.prototype,'eval',async(_script,_count,key)=>{keys.push(key);return mode==='limit'?21:++attempts;});
 const user={id:'employee',active:true,passwordSet:true,email:'employee@example.invalid',passwordHash:hashPassword('synthetic-password')};
 const db={
  $queryRaw:async()=>[{value:1}],
  session:{create:async({data})=>{sessions.set(data.id,{...data,user});},findUnique:async({where})=>sessions.get(where.id),deleteMany:async({where})=>{sessions.delete(where.id);}},
  user:{findUnique:async()=>user},
  membership:{findUnique:async({where})=>where.userId_companyId.companyId==='own-company'?{role:'EMPLOYEE',employee:{id:'employee-record'}}:null},
 };
 const app=await createApp(db);
 await app.listen(0,'127.0.0.1');t.after(()=>app.close());
 const base=await app.getUrl();
 const request=(path,options={})=>fetch(base+path,options);
 const login=()=>request('/api/auth/login',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:JSON.stringify({email:user.email,password:'synthetic-password'})});
 assert.equal((await request('/health')).status,200);
 assert.equal((await request('/api/health')).status,200);
 for(const supplied of [undefined,'https://evil.example.invalid']){
  const headers={'Content-Type':'application/json'};if(supplied)headers.Origin=supplied;
  assert.equal((await request('/api/auth/login',{method:'POST',headers,body:'{}'})).status,403);
 }
 const response=await login();assert.equal(response.status,201);
 const cookie=response.headers.get('set-cookie');
 for(const flag of [/HttpOnly/i,/Secure/i,/SameSite=Strict/i,/Path=\//i])assert.match(cookie,flag);
 const session=cookie.split(';')[0];
 assert.ok(sessions.has(digest(session.split('=')[1])));
 assert.equal((await request('/api/audit',{headers:{Cookie:session,'x-company-id':'forged-company'}})).status,403);
 assert.equal((await request('/api/members',{method:'POST',headers:{Cookie:session,'x-company-id':'own-company',Origin:origin,'Content-Type':'application/json'},body:'{}'})).status,403);
 assert.equal((await request('/api/biometric/device',{method:'POST'})).status,403);
 const cors=await request('/api/auth/login',{method:'OPTIONS',headers:{Origin:origin,'Access-Control-Request-Method':'POST'}});
 assert.equal(cors.headers.get('access-control-allow-origin'),origin);
 const foreign=await request('/api/auth/login',{method:'OPTIONS',headers:{Origin:'https://evil.example.invalid','Access-Control-Request-Method':'POST'}});
 assert.notEqual(foreign.headers.get('access-control-allow-origin'),'https://evil.example.invalid');
 mode='limit';assert.equal((await login()).status,429);
 // Untrusted direct peers cannot spoof the IP used as a Redis rate-limit key.
 await request('/api/auth/login',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json','X-Forwarded-For':'203.0.113.7'},body:JSON.stringify({email:user.email,password:'synthetic-password'})});
 assert.ok(!keys.includes('login:ip:203.0.113.7'));
 const trust=app.getHttpAdapter().getInstance().get('trust proxy fn');
 assert.equal(trust('172.30.10.2'),true);assert.equal(trust('203.0.113.7'),false);
 const logout=await request('/api/auth/logout',{method:'POST',headers:{Cookie:session,Origin:origin}});
 assert.match(logout.headers.get('set-cookie'),/Secure/);assert.equal(sessions.size,0);
 assert.equal((await request('/api/audit',{headers:{Cookie:session,'x-company-id':'own-company'}})).status,401);
});
