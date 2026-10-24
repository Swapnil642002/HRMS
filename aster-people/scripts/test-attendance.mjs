// Integration tests use an isolated synthetic company in the configured local DB.
// Run after migrations and API build: node --env-file=.env scripts/test-attendance.mjs
import 'reflect-metadata';
import assert from 'node:assert/strict';
import { randomBytes, randomUUID, createHash, generateKeyPairSync, sign } from 'node:crypto';
import { PrismaClient } from '@prisma/client';
import { isoCBOR } from '@simplewebauthn/server/helpers';
import { AttendanceController } from '../apps/api/dist/attendance.js';
import { IntegrationController, PeopleController } from '../apps/api/dist/routes.js';
assert.ok(['localhost','127.0.0.1','[::1]'].includes(new URL(process.env.DATABASE_URL).hostname),'Attendance integration checks must use a local database');
const db=new PrismaClient();
const controller=new AttendanceController(db);
const origin=process.env.APP_ORIGIN;
assert.ok(origin,'APP_ORIGIN is required');
const rpID=new URL(origin).hostname;
const sha=(b)=>createHash('sha256').update(b).digest();
const b64=(b)=>Buffer.from(b).toString('base64url');
const pair=generateKeyPairSync('ec',{namedCurve:'prime256v1'});
const jwk=pair.publicKey.export({format:'jwk'});
const publicKey=isoCBOR.encode(new Map([[1,2],[3,-7],[-1,1],[-2,new Uint8Array(Buffer.from(jwk.x,'base64url'))],[-3,new Uint8Array(Buffer.from(jwk.y,'base64url'))]]));
const credentialBytes=randomBytes(32),credentialId=b64(credentialBytes);
let counter=0,passed=0;
let company,otherCompany;
const userId=randomUUID();
const employeeRequest={headers:{cookie:'aster_session='+randomBytes(32).toString('hex')},auth:{companyId:'',employeeId:'',userId,role:'EMPLOYEE'}};
const authData=(flags,count)=>{const buf=Buffer.alloc(37);sha(rpID).copy(buf);buf[32]=flags;buf.writeUInt32BE(count,33);return buf;};
function registration(options){
 const length=Buffer.alloc(2);length.writeUInt16BE(credentialBytes.length);
 const data=Buffer.concat([authData(0x45,0),Buffer.alloc(16),length,credentialBytes,publicKey]);
 return {id:credentialId,rawId:credentialId,type:'public-key',authenticatorAttachment:'platform',clientExtensionResults:{},response:{clientDataJSON:b64(JSON.stringify({type:'webauthn.create',challenge:options.challenge,origin,crossOrigin:false})),attestationObject:b64(isoCBOR.encode(new Map([['fmt','none'],['attStmt',new Map()],['authData',new Uint8Array(data)]]))),transports:['internal']}};
}
function assertion(options,{flags=5,wrongOrigin=false,wrongChallenge=false,badSignature=false}={}){
 const data=authData(flags,++counter);
 const client=Buffer.from(JSON.stringify({type:'webauthn.get',challenge:wrongChallenge?'wrong':options.challenge,origin:wrongOrigin?'https://wrong.example':origin,crossOrigin:false}));
 const signature=sign('sha256',Buffer.concat([data,sha(client)]),pair.privateKey);
 if(badSignature)signature[signature.length-1]^=1;
 return {id:credentialId,rawId:credentialId,type:'public-key',clientExtensionResults:{},response:{authenticatorData:b64(data),clientDataJSON:b64(client),signature:b64(signature)}};
}
const test=async(name,fn)=>{await fn();console.log('PASS '+name);passed++;};
try{
 company=await db.company.create({data:{name:'Attendance integration '+randomUUID()}});
 otherCompany=await db.company.create({data:{name:'Foreign attendance integration '+randomUUID()}});
 employeeRequest.auth.companyId=company.id;
 const employee=await db.employee.create({data:{companyId:company.id,code:'TEST-1',name:'Synthetic employee',email:randomUUID()+'@example.invalid',department:'Test',designation:'Test',joinedOn:new Date('2020-01-01'),monthlyBasic:100000}});
 employeeRequest.auth.employeeId=employee.id;
 const admin={headers:{cookie:'aster_session=test-admin'},auth:{companyId:company.id,employeeId:null,userId:randomUUID(),role:'HR'}};
 const location=()=>({latitude:19.076,longitude:72.8777,accuracyMetres:5,capturedAt:Date.now()});
 let branch;
 const options=(direction='IN')=>controller.punchOptions(employeeRequest,{direction,location:location()});
 const finish=(o,extra={},req=employeeRequest)=>controller.punch(req,{challengeId:o.challengeId,response:assertion(o.options,extra)});
 await test('unassigned and unapproved employees cannot punch',async()=>{
  await assert.rejects(options(),/assign your branch/);
  branch=await controller.createBranch(admin,{name:'Test branch',city:'Mumbai',latitude:19.076,longitude:72.8777});
  assert.equal(branch.radiusMetres,50);
  await controller.assign(admin,{employeeId:employee.id,branchId:branch.id});
  await assert.rejects(options(),/approve it first/);
 });
 await test('employees cannot manage branches or approve passkeys',async()=>{
  await assert.rejects(controller.createBranch(employeeRequest,{name:'Fake',city:'Fake',latitude:0,longitude:0}),/role/);
  await assert.rejects(controller.review(employeeRequest,credentialId,{decision:'APPROVED'}),/role/);
 });
 await test('real WebAuthn registration is pending until another HR user approves',async()=>{
  const o=await controller.registerOptions(employeeRequest,{label:'Synthetic authenticator'});
  assert.equal(o.options.authenticatorSelection.userVerification,'required');
  const payload={challengeId:o.challengeId,response:registration(o.options)};
  await controller.registerVerify(employeeRequest,payload);
  await assert.rejects(controller.registerVerify(employeeRequest,payload),/expired or was already used/);
  assert.equal((await db.attendanceCredential.findUnique({where:{id:credentialId}})).status,'PENDING');
  await assert.rejects(options(),/approve it first/);
  await assert.rejects(controller.review({...employeeRequest,auth:{...employeeRequest.auth,role:'HR'}},credentialId,{decision:'APPROVED'}),/Another HR/);
  await controller.review(admin,credentialId,{decision:'APPROVED'});
 });
 await test('cross-company assignment and review are rejected',async()=>{
  const foreign=await db.branch.create({data:{companyId:otherCompany.id,name:'Foreign',city:'Delhi',latitude:28.61,longitude:77.2}});
  await assert.rejects(controller.assign(admin,{employeeId:employee.id,branchId:foreign.id}),/active branch/);
  await assert.rejects(controller.review({...admin,auth:{...admin.auth,companyId:otherCompany.id}},credentialId,{decision:'REVOKED'}));
 });
 await test('out-of-fence, inaccurate and stale locations are rejected',async()=>{
  for(const override of [{latitude:19.08},{accuracyMetres:100},{capturedAt:Date.now()-180000}])await assert.rejects(controller.punchOptions(employeeRequest,{direction:'IN',location:{...location(),...override}}));
 });
 await test('challenges are bound to session and company',async()=>{
  const o=await options();
  await assert.rejects(finish(o,{}, {...employeeRequest,headers:{cookie:'aster_session=different'}}),/expired or was already used/);
  const fake={...employeeRequest,auth:{...employeeRequest.auth,companyId:otherCompany.id}};
  await assert.rejects(finish(o,{},fake));
 });
 await test('expired challenge and branch changes invalidate a punch',async()=>{
  let o=await options();
  await db.attendanceChallenge.update({where:{id:o.challengeId},data:{expiresAt:new Date(0)}});
  await assert.rejects(finish(o),/expired or was already used/);
  o=await options();await db.employee.update({where:{id:employee.id},data:{branchId:null}});
  await assert.rejects(finish(o),/Branch assignment changed/);
  await controller.assign(admin,{employeeId:employee.id,branchId:branch.id});
 });
 for(const [name,override] of [['user verification missing',{flags:1}],['wrong origin',{wrongOrigin:true}],['wrong signed challenge',{wrongChallenge:true}],['tampered signature',{badSignature:true}]]){
  await test(name+' is rejected and consumes its challenge',async()=>{
   const o=await options();await assert.rejects(finish(o,override),/verification (failed|is required)/);
   await assert.rejects(finish(o),/expired or was already used/);
  });
 }
 await test('valid signature checks in and records proof; replay is rejected',async()=>{
  const o=await options();assert.equal(o.options.userVerification,'required');
  const row=await controller.punch(employeeRequest,{challengeId:o.challengeId,response:assertion(o.options),direction:'OUT'});
  assert.equal(row.source,'VERIFIED');assert.equal(row.checkOut,null);
  assert.equal(await db.attendanceProof.count({where:{companyId:company.id,direction:'IN'}}),1);
  await assert.rejects(finish(o),/expired or was already used/);
  const duplicate=await options();await assert.rejects(finish(duplicate),/Already checked in/);
 });
 await test('concurrent checkout accepts one response and produces one proof',async()=>{
  const o=await options('OUT');const response=assertion(o.options);
  const results=await Promise.allSettled([controller.punch(employeeRequest,{challengeId:o.challengeId,response}),controller.punch(employeeRequest,{challengeId:o.challengeId,response})]);
  assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
  assert.equal(await db.attendanceProof.count({where:{companyId:company.id,direction:'OUT'}}),1);
 });
 await test('revocation between challenge and punch is enforced',async()=>{
  const o=await options();await controller.review(admin,credentialId,{decision:'REVOKED'});
  await assert.rejects(finish(o),/not approved/);
 });
 await test('old unverified punch paths are unavailable',async()=>{
  assert.equal(PeopleController.prototype.punch,undefined);
  await assert.rejects(new IntegrationController(db).ingest(),/disabled/);
  await assert.rejects(controller.punch(employeeRequest,{direction:'IN'}));
 });
 console.log(`Completed ${passed} attendance integration checks.`);
}finally{
 for(const co of [company,otherCompany].filter(Boolean)){
  for(const model of ['attendanceProof','attendanceChallenge','attendanceCredential','attendance','audit','employee','branch'])await db[model].deleteMany({where:{companyId:co.id}});
  await db.company.delete({where:{id:co.id}});
 }
 await db.$disconnect();
}
