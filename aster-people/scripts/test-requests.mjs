// Local integration test: synthetic tenants and users are removed in finally.
import 'reflect-metadata';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { PrismaClient } from '@prisma/client';
import { WorkflowController } from '../apps/api/dist/requests.js';
assert.ok(['localhost','127.0.0.1','[::1]'].includes(new URL(process.env.DATABASE_URL).hostname),'Use a local test database');
const db=new PrismaClient(),controller=new WorkflowController(db);
const companies=[],users=[],emails=[];let passed=0;
const check=async(name,fn)=>{await fn();console.log('PASS '+name);passed++;};
try{
 const company=await db.company.create({data:{name:'Request test '+randomUUID()}});companies.push(company.id);
 const foreign=await db.company.create({data:{name:'Foreign request test '+randomUUID()}});companies.push(foreign.id);
 async function account(role,companyId=company.id){const email=randomUUID()+'@example.invalid';emails.push(email);const u=await db.user.create({data:{name:'Synthetic '+role,email,passwordHash:'unusable-test-hash',passwordSet:false}});users.push(u.id);const m=await db.membership.create({data:{userId:u.id,companyId,role}});return {membership:m,request:{auth:{userId:u.id,companyId,role,employeeId:null}}};}
 const hr=await account('HR'),manager=await account('MANAGER'),otherManager=await account('MANAGER'),finance=await account('FINANCE'),admin=await account('ADMIN'),staff=await account('EMPLOYEE'),outsider=await account('MANAGER',foreign.id);
 const employee=await db.employee.create({data:{companyId:company.id,membershipId:staff.membership.id,code:'TEST-1',name:'Synthetic employee',email:emails[5],department:'Test',designation:'Test',joinedOn:new Date('2020-01-01'),monthlyBasic:100000}});staff.request.auth.employeeId=employee.id;
 const bodies={LEAVE:{details:{from:'2028-01-10',to:'2028-01-11',reason:'Test leave'}},TRAVEL:{amount:12550,details:{from:'2028-02-10',to:'2028-02-11',destination:'Test city',purpose:'Test visit'}},EXPENSE:{amount:12550,details:{category:'MEALS',incurredOn:'2028-01-10',description:'Test meal'}},ADVANCE:{amount:12550,details:{reason:'Test advance'}},LOAN:{amount:12550,details:{reason:'Test loan',installments:12}}};
 const create=kind=>controller.create(staff.request,{kind,employeeId:employee.id,title:'Synthetic '+kind,...bodies[kind]});
 const approve=(who,id)=>controller.decide(who.request,id,{decision:'APPROVE',note:'Reviewed in test'});
 await check('employee cannot submit before HR assigns a manager',async()=>{await assert.rejects(create('LEAVE'),/assign an active reporting manager/);});
 await check('only HR or Admin can assign an active same-company manager',async()=>{
  await assert.rejects(controller.assign(staff.request,{employeeId:employee.id,managerId:manager.membership.id}),/role/);
  await assert.rejects(controller.assign(hr.request,{employeeId:employee.id,managerId:outsider.membership.id}),/active Manager/);
  await assert.rejects(controller.assign(hr.request,{employeeId:employee.id,managerId:finance.membership.id}),/active Manager/);
  await controller.assign(hr.request,{employeeId:employee.id,managerId:manager.membership.id});
 });
 for(const kind of Object.keys(bodies))await check(kind+' routes only to its manager, then the correct final role',async()=>{
  const q=await create(kind);assert.equal(q.managerId,manager.membership.id);
  assert.ok((await controller.list(manager.request)).some(row=>row.id===q.id&&row.canReview));
  assert.ok(!(await controller.list(otherManager.request)).some(row=>row.id===q.id));
  await assert.rejects(approve(otherManager,q.id),/Only the assigned/);
  await assert.rejects(approve(admin,q.id),/Only the assigned/);
  await assert.rejects(approve(staff,q.id),/own request/);
  await assert.rejects(approve(outsider,q.id));
  await assert.rejects(controller.pay(finance.request,q.id),/Only approved/);
  const first=await approve(manager,q.id);assert.equal(first.status,'PENDING');assert.equal(first.approvalStep,1);
  await assert.rejects(approve(manager,q.id),/different person/);
  assert.equal((await controller.list(manager.request)).find(row=>row.id===q.id).canReview,false);
  const finalRole=kind==='LEAVE'?hr:finance;
  await assert.rejects(approve(kind==='LEAVE'?finance:hr,q.id),/role cannot review/);
  const final=await approve(finalRole,q.id);assert.equal(final.status,'APPROVED');
  if(['EXPENSE','ADVANCE','LOAN'].includes(kind)){assert.equal((await controller.pay(finance.request,q.id)).status,'PAID');await assert.rejects(controller.pay(finance.request,q.id),/Only approved/);}
 });
 await check('HR reassignment moves only requests waiting for manager approval',async()=>{
  const waiting=await create('ADVANCE'),stageTwo=await create('LOAN');await approve(manager,stageTwo.id);
  const legacy=await db.workRequest.create({data:{companyId:company.id,employeeId:employee.id,kind:'ADVANCE',title:'Legacy pending',details:{reason:'Legacy'},amount:100,approvalRoles:['MANAGER','FINANCE']}});
  const changed=await controller.assign(hr.request,{employeeId:employee.id,managerId:otherManager.membership.id});assert.equal(changed.rerouted,2);
  assert.equal((await db.workRequest.findUnique({where:{id:waiting.id}})).managerId,otherManager.membership.id);
  assert.equal((await db.workRequest.findUnique({where:{id:legacy.id}})).managerId,otherManager.membership.id);
  assert.equal((await db.workRequest.findUnique({where:{id:stageTwo.id}})).managerId,manager.membership.id);
  await assert.rejects(approve(manager,waiting.id),/Only the assigned/);await approve(otherManager,waiting.id);
 });
 await check('rejection ends the request and prevents later approval or payment',async()=>{
  const q=await create('EXPENSE');const rejected=await controller.decide(otherManager.request,q.id,{decision:'REJECT',note:'Not approved in test'});assert.equal(rejected.status,'REJECTED');await assert.rejects(approve(finance,q.id),/no longer pending/);await assert.rejects(controller.pay(finance.request,q.id),/Only approved/);
 });
 await check('inactive manager cannot receive new requests or approve existing ones',async()=>{
  const q=await create('ADVANCE');await db.user.update({where:{id:otherManager.membership.userId},data:{active:false}});await assert.rejects(create('LOAN'),/active reporting manager/);await assert.rejects(approve(otherManager,q.id),/Only the assigned/);await db.user.update({where:{id:otherManager.membership.userId},data:{active:true}});
 });
 await check('self-manager assignment is rejected even for HR accounts',async()=>{
  const e=await db.employee.create({data:{companyId:company.id,membershipId:hr.membership.id,code:'TEST-HR',name:'Synthetic HR',email:emails[0],department:'Test',designation:'HR',joinedOn:new Date('2020-01-01'),monthlyBasic:100000}});
  await assert.rejects(controller.assign(admin.request,{employeeId:e.id,managerId:hr.membership.id}),/own reporting manager/);
 });
 console.log(`Completed ${passed} request-routing integration checks.`);
}finally{
 for(const companyId of companies){await db.approvalDecision.deleteMany({where:{request:{companyId}}});await db.workRequest.deleteMany({where:{companyId}});await db.audit.deleteMany({where:{companyId}});await db.employee.deleteMany({where:{companyId}});await db.membership.deleteMany({where:{companyId}});await db.company.delete({where:{id:companyId}});}
 await db.outbox.deleteMany({where:{recipient:{in:emails}}});await db.user.deleteMany({where:{id:{in:users}}});await db.$disconnect();
}
