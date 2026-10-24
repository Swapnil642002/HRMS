import { Controller, Get, Post, Body, Param, Req, Res, UseGuards, BadRequestException, ConflictException, NotFoundException, ForbiddenException } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { z } from 'zod';
import type { Response } from 'express';
import { Db } from './db';
import { AuthGuard, AuthRequest, Context, requireRole, ownOrStaff } from './auth';
import { text, requestInput, days } from './validation';
import { documentPdf } from './pdf';
const serial={isolationLevel:Prisma.TransactionIsolationLevel.Serializable};
const managerRoles=['MANAGER','HR','ADMIN'] as const;
const audit=(tx:Prisma.TransactionClient,c:Context,action:string,entityId:string)=>tx.audit.create({data:{companyId:c.companyId,actorId:c.userId,action,entityId}});
const managerSelect={id:true,role:true,userId:true,user:{select:{name:true,active:true}}} as const;
const requestInclude={employee:{select:{name:true,code:true,membership:{select:{userId:true}}}},manager:{select:managerSelect},decisions:{orderBy:{step:'asc' as const}}};
type ReviewRequest=Prisma.WorkRequestGetPayload<{include:typeof requestInclude}>;
function canReview(c:Context,q:ReviewRequest){
 if(q.status!=='PENDING'||q.employee.membership?.userId===c.userId||q.decisions.some(d=>d.actorId===c.userId))return false;
 if(q.approvalStep===0)return !!q.manager&&q.manager.userId===c.userId&&q.manager.user.active&&managerRoles.some(role=>role===q.manager!.role);
 return c.role==='ADMIN'||c.role===q.approvalRoles[q.approvalStep];
}
@Controller('requests')
@UseGuards(AuthGuard)
export class WorkflowController {
 constructor(private db:Db){}
 @Get('managers')async managers(@Req()r:AuthRequest){requireRole(r.auth,'ADMIN','HR');return this.db.membership.findMany({where:{companyId:r.auth.companyId,role:{in:[...managerRoles]},user:{active:true}},select:managerSelect,orderBy:{user:{name:'asc'}}});}
 @Post('assign-manager')async assign(@Req()r:AuthRequest,@Body()body:unknown){
  const c=r.auth;requireRole(c,'ADMIN','HR');const b=z.object({employeeId:text,managerId:text}).strict().parse(body);
  return this.db.$transaction(async tx=>{
   const employee=await tx.employee.findFirst({where:{id:b.employeeId,companyId:c.companyId,active:true},include:{membership:true}});
   if(!employee)throw new NotFoundException('Select an active employee');
   const manager=await tx.membership.findFirst({where:{id:b.managerId,companyId:c.companyId,role:{in:[...managerRoles]},user:{active:true}},include:{user:true}});
   if(!manager)throw new BadRequestException('Select an active Manager, HR or Admin account in this company');
   if(employee.membership?.userId===manager.userId)throw new BadRequestException('An employee cannot be their own reporting manager');
   await tx.employee.update({where:{id:employee.id},data:{reportingManagerId:manager.id}});
   // Reassignment also routes requests still waiting for their first approval.
   const waiting=await tx.workRequest.findMany({where:{companyId:c.companyId,employeeId:employee.id,status:'PENDING',approvalStep:0},select:{id:true}});
   await tx.workRequest.updateMany({where:{companyId:c.companyId,employeeId:employee.id,status:'PENDING',approvalStep:0},data:{managerId:manager.id}});
   await audit(tx,c,'employee.reporting_manager_assigned',employee.id);
   for(const q of waiting)await audit(tx,c,'request.manager_reassigned',q.id);
   return {ok:true,rerouted:waiting.length};
  },serial);
 }
 @Get()async list(@Req()r:AuthRequest){
  const c=r.auth,all=['ADMIN','HR','FINANCE'].includes(c.role);
  const rows=await this.db.workRequest.findMany({where:{companyId:c.companyId,...(all?{}:{OR:[{employeeId:c.employeeId??'__none__'},{manager:{userId:c.userId}}]})},include:requestInclude,orderBy:{createdAt:'desc'},take:300});
  return rows.map(q=>({...q,canReview:canReview(c,q),canDownloadTour:q.kind==='TRAVEL'&&(all||q.employeeId===c.employeeId||q.manager?.userId===c.userId)}));
 }
 @Post()async create(@Req()r:AuthRequest,@Body()body:unknown){
  const c=r.auth,b=requestInput.parse(body);ownOrStaff(c,b.employeeId,'ADMIN','HR');let duration=0;
  if(b.kind==='LEAVE'||b.kind==='TRAVEL'){try{duration=days(b.details.from,b.details.to);}catch{throw new BadRequestException('Invalid date range');}if(b.details.from.slice(0,4)!==b.details.to.slice(0,4))throw new BadRequestException('Split requests across calendar years');}
  return this.db.$transaction(async tx=>{
   const e=await tx.employee.findFirst({where:{id:b.employeeId,companyId:c.companyId,active:true},include:{reportingManager:{include:{user:true}},membership:true}});
   if(!e)throw new NotFoundException('Employee not found');
   const manager=e.reportingManager;
   if(!manager||!manager.user.active||!managerRoles.some(role=>role===manager.role))throw new BadRequestException('Ask HR to assign an active reporting manager before submitting a request');
   if(e.membership?.userId===manager.userId)throw new BadRequestException('Ask HR to assign a different reporting manager');
   if(b.kind==='LEAVE'){
    const all=await tx.workRequest.findMany({where:{companyId:c.companyId,employeeId:e.id,kind:'LEAVE',status:{in:['PENDING','APPROVED']}}});let used=0;
    for(const q of all){const d=q.details as {from:string;to:string};if(d.from<=b.details.to&&d.to>=b.details.from)throw new ConflictException('Leave overlaps an existing request');if(d.from.slice(0,4)===b.details.from.slice(0,4))used+=days(d.from,d.to);}
    if(used+duration>e.leaveAllowance)throw new BadRequestException('Insufficient leave balance');
   }
   if(b.kind==='EXPENSE'&&b.details.travelId&&!await tx.workRequest.findFirst({where:{id:b.details.travelId,companyId:c.companyId,employeeId:e.id,kind:'TRAVEL',status:'APPROVED'}}))throw new BadRequestException('Select an approved tour for this employee');
   const request=await tx.workRequest.create({data:{companyId:c.companyId,employeeId:e.id,managerId:manager.id,kind:b.kind,title:b.title,details:b.details,amount:'amount'in b?b.amount:0,approvalRoles:b.kind==='LEAVE'?['MANAGER','HR']:['MANAGER','FINANCE']}});
   await audit(tx,c,'request.created',request.id);return request;
  },serial);
 }
 @Post(':id/decide')async decide(@Req()r:AuthRequest,@Param('id')id:string,@Body()body:unknown){
  const c=r.auth,b=z.object({decision:z.enum(['APPROVE','REJECT']),note:z.string().trim().min(3).max(1000)}).parse(body);
  return this.db.$transaction(async tx=>{
   const q=await tx.workRequest.findFirst({where:{id,companyId:c.companyId},include:requestInclude});if(!q)throw new NotFoundException();
   if(q.status!=='PENDING')throw new ConflictException('Request is no longer pending');
   if(q.employee.membership?.userId===c.userId)throw new ForbiddenException('You cannot approve your own request');
   if(q.decisions.some(d=>d.actorId===c.userId))throw new ForbiddenException('A different person must approve the next stage');
   if(q.approvalStep===0&&!q.manager)throw new BadRequestException('HR must assign a reporting manager to route this request');
   if(!canReview(c,q))throw new ForbiddenException(q.approvalStep===0?'Only the assigned reporting manager can review this request':'Your role cannot review this approval stage');
   await tx.approvalDecision.create({data:{requestId:id,actorId:c.userId,step:q.approvalStep,decision:b.decision,note:b.note}});
   const final=b.decision==='REJECT'||q.approvalStep+1===q.approvalRoles.length,status=b.decision==='REJECT'?'REJECTED':final?'APPROVED':'PENDING';
   const updated=await tx.workRequest.update({where:{id},data:{status,approvalStep:q.approvalStep+1}});
   await audit(tx,c,`request.${b.decision.toLowerCase()}`,id);
   // Retain the existing employee notification workflow.
   const employee=await tx.employee.findUniqueOrThrow({where:{id:q.employeeId},select:{email:true}});
   await tx.outbox.create({data:{recipient:employee.email,subject:'Your request has an update',body:`${q.title}: ${status}. Sign in to view the decision.`}});return updated;
  },serial);
 }
 @Post(':id/pay')async pay(@Req()r:AuthRequest,@Param('id')id:string){const c=r.auth;requireRole(c,'FINANCE','ADMIN');return this.db.$transaction(async tx=>{const q=await tx.workRequest.findFirst({where:{id,companyId:c.companyId}});if(!q||!['EXPENSE','ADVANCE','LOAN'].includes(q.kind)||q.status!=='APPROVED')throw new ConflictException('Only approved financial claims can be marked paid');const result=await tx.workRequest.update({where:{id},data:{status:'PAID'}});await audit(tx,c,'request.payment_recorded',id);return result;},serial);}
 @Get(':id/tour-bill.pdf')async tourBill(@Req()r:AuthRequest,@Param('id')id:string,@Res()res:Response){
  const c=r.auth,tour=await this.db.workRequest.findFirst({where:{id,companyId:c.companyId,kind:'TRAVEL'},include:{employee:true,company:true,manager:true}});if(!tour)throw new NotFoundException();
  if(c.employeeId!==tour.employeeId&&tour.manager?.userId!==c.userId)requireRole(c,'ADMIN','HR','FINANCE');
  const claims=await this.db.workRequest.findMany({where:{companyId:c.companyId,employeeId:tour.employeeId,kind:'EXPENSE',status:{in:['APPROVED','PAID']},details:{path:['travelId'],equals:id}}});const cents=(n:number)=>`INR ${(n/100).toFixed(2)}`;
  const data=await documentPdf(tour.company.name,'Tour bill',[[tour.title,tour.employee.name],['Status',tour.status],...claims.map(q=>[q.title,cents(q.amount)]),['Total approved expenses',cents(claims.reduce((a,q)=>a+q.amount,0))]]);res.type('application/pdf').attachment('tour-bill.pdf').send(Buffer.from(data));
 }
}
