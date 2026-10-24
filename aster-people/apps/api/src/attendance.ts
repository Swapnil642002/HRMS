import { BadRequestException, Body, ConflictException, Controller, ForbiddenException, Get, NotFoundException, Param, Post, Req, UseGuards } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { generateRegistrationOptions, verifyRegistrationResponse, generateAuthenticationOptions, verifyAuthenticationResponse, type RegistrationResponseJSON, type AuthenticationResponseJSON } from '@simplewebauthn/server';
import { z } from 'zod';
import { Db } from './db';
import { AuthGuard, AuthRequest, requireRole } from './auth';
import { digest, tokenFromCookie } from './security';
import { checkLocation, locationInput } from './attendance-policy';
const serial={isolationLevel:Prisma.TransactionIsolationLevel.Serializable};
const text=z.string().trim().min(1).max(160);
const branchInput=z.object({name:text,city:text,latitude:z.number().min(-90).max(90),longitude:z.number().min(-180).max(180),active:z.boolean().default(true)});
const envelope=z.object({challengeId:text,response:z.object({id:z.string().min(1).max(2048)}).passthrough()});
function relyingParty(){const url=new URL(process.env.APP_ORIGIN!);return {origin:url.origin,rpID:url.hostname};}
@Controller('attendance')
@UseGuards(AuthGuard)
export class AttendanceController {
 constructor(private db:Db){}
 private async employee(r:AuthRequest){
  if(!r.auth.employeeId)throw new ForbiddenException('Your account has no employee profile');
  const employee=await this.db.employee.findFirst({where:{id:r.auth.employeeId,companyId:r.auth.companyId,active:true},include:{branch:true}});
  if(!employee)throw new ForbiddenException('Employee profile is inactive');return employee;
 }
 private async issue(r:AuthRequest,purpose:string,challenge:string,payload:Prisma.InputJsonValue){
  return this.db.$transaction(async tx=>{
   await tx.attendanceChallenge.deleteMany({where:{OR:[{expiresAt:{lt:new Date()}},{companyId:r.auth.companyId,userId:r.auth.userId,purpose}]}});
   return tx.attendanceChallenge.create({data:{companyId:r.auth.companyId,employeeId:r.auth.employeeId!,userId:r.auth.userId,sessionHash:digest(tokenFromCookie(r.headers.cookie)!),purpose,challenge,payload,expiresAt:new Date(Date.now()+120000)}});
  },serial);
 }
 private async consume(r:AuthRequest,id:string,purpose:string){
  // Delete before verification, so even invalid attempts cannot reuse a challenge.
  return this.db.$transaction(async tx=>{
   const challenge=await tx.attendanceChallenge.findFirst({where:{id,companyId:r.auth.companyId,employeeId:r.auth.employeeId!,userId:r.auth.userId,sessionHash:digest(tokenFromCookie(r.headers.cookie)!),purpose,expiresAt:{gt:new Date()}}});
   if(!challenge)throw new BadRequestException('Verification expired or was already used. Start again.');
   const deleted=await tx.attendanceChallenge.deleteMany({where:{id:challenge.id}});
   if(deleted.count!==1)throw new ConflictException('Verification was already used');return challenge;
  },serial);
 }
 @Get('security')async security(@Req()r:AuthRequest){
  const e=await this.employee(r);
  const credentials=await this.db.attendanceCredential.findMany({where:{companyId:r.auth.companyId,employeeId:e.id,userId:r.auth.userId},select:{id:true,label:true,status:true,createdAt:true}});
  return {branch:e.branch,credentials};
 }
 @Get('branches')async branches(@Req()r:AuthRequest){requireRole(r.auth,'ADMIN','HR');return this.db.branch.findMany({where:{companyId:r.auth.companyId},orderBy:{name:'asc'}});}
 @Post('branches')async createBranch(@Req()r:AuthRequest,@Body()body:unknown){requireRole(r.auth,'ADMIN','HR');const b=branchInput.parse(body);return this.db.$transaction(async tx=>{
  const branch=await tx.branch.create({data:{...b,companyId:r.auth.companyId,radiusMetres:50,maxAccuracyMetres:25}});
  await tx.audit.create({data:{companyId:r.auth.companyId,actorId:r.auth.userId,action:'attendance.branch_created',entityId:branch.id}});return branch;
 });}
 @Post('branches/:id')async updateBranch(@Req()r:AuthRequest,@Param('id')id:string,@Body()body:unknown){requireRole(r.auth,'ADMIN','HR');const b=branchInput.parse(body);return this.db.$transaction(async tx=>{
  const result=await tx.branch.updateMany({where:{id,companyId:r.auth.companyId},data:{...b,radiusMetres:50,maxAccuracyMetres:25}});if(!result.count)throw new NotFoundException();
  await tx.audit.create({data:{companyId:r.auth.companyId,actorId:r.auth.userId,action:'attendance.branch_updated',entityId:id}});return {ok:true};
 });}
 @Post('assignments')async assign(@Req()r:AuthRequest,@Body()body:unknown){requireRole(r.auth,'ADMIN','HR');const b=z.object({employeeId:text,branchId:text}).parse(body);return this.db.$transaction(async tx=>{
  if(!await tx.branch.findFirst({where:{id:b.branchId,companyId:r.auth.companyId,active:true}}))throw new BadRequestException('Select an active branch');
  const result=await tx.employee.updateMany({where:{id:b.employeeId,companyId:r.auth.companyId,active:true},data:{branchId:b.branchId}});if(!result.count)throw new NotFoundException();
  await tx.audit.create({data:{companyId:r.auth.companyId,actorId:r.auth.userId,action:'attendance.branch_assigned',entityId:b.employeeId}});return {ok:true};
 },serial);}
 @Get('credentials')async credentials(@Req()r:AuthRequest){requireRole(r.auth,'ADMIN','HR');return this.db.attendanceCredential.findMany({where:{companyId:r.auth.companyId,status:{in:['PENDING','APPROVED']}},select:{id:true,label:true,status:true,createdAt:true,employee:{select:{name:true,code:true}}},orderBy:{createdAt:'desc'},take:500});}
 @Post('credentials/:id/review')async review(@Req()r:AuthRequest,@Param('id')id:string,@Body()body:unknown){requireRole(r.auth,'ADMIN','HR');const b=z.object({decision:z.enum(['APPROVED','REVOKED'])}).parse(body);return this.db.$transaction(async tx=>{
  const credential=await tx.attendanceCredential.findFirst({where:{id,companyId:r.auth.companyId}});if(!credential)throw new NotFoundException();
  if(b.decision==='APPROVED'&&credential.userId===r.auth.userId)throw new ForbiddenException('Another HR administrator must approve your attendance passkey');
  if(b.decision==='APPROVED'&&credential.status!=='PENDING')throw new ConflictException('Only pending passkeys can be approved');
  await tx.attendanceCredential.update({where:{id},data:{status:b.decision,reviewedBy:r.auth.userId}});
  await tx.audit.create({data:{companyId:r.auth.companyId,actorId:r.auth.userId,action:`attendance.passkey_${b.decision.toLowerCase()}`,entityId:id}});return {ok:true};
 },serial);}
 @Post('registration/options')async registerOptions(@Req()r:AuthRequest,@Body()body:unknown){
  const e=await this.employee(r),b=z.object({label:text}).parse(body),{rpID}=relyingParty();
  const existing=await this.db.attendanceCredential.findMany({where:{companyId:r.auth.companyId,employeeId:e.id}});
  if(existing.filter(c=>c.status!=='REVOKED').length>=3)throw new BadRequestException('Ask HR to revoke an unused passkey before adding another');
  const options=await generateRegistrationOptions({rpName:'Daksha Attendance',rpID,userName:e.email,userDisplayName:e.name,userID:new TextEncoder().encode(digest(r.auth.companyId+':'+r.auth.userId)),attestationType:'none',supportedAlgorithmIDs:[-7,-257],authenticatorSelection:{authenticatorAttachment:'platform',residentKey:'required',userVerification:'required'},excludeCredentials:existing.map(c=>({id:c.id})),timeout:120000});
  const challenge=await this.issue(r,'REGISTER',options.challenge,{label:b.label});return {challengeId:challenge.id,options};
 }
 @Post('registration/verify')async registerVerify(@Req()r:AuthRequest,@Body()body:unknown){
  await this.employee(r);const b=envelope.parse(body),challenge=await this.consume(r,b.challengeId,'REGISTER'),{origin,rpID}=relyingParty();
  let result;try{result=await verifyRegistrationResponse({response:b.response as unknown as RegistrationResponseJSON,expectedChallenge:challenge.challenge,expectedOrigin:origin,expectedRPID:rpID,requireUserVerification:true,supportedAlgorithmIDs:[-7,-257]});}catch{throw new BadRequestException('Passkey registration could not be verified');}
  if(!result.verified||!result.registrationInfo)throw new BadRequestException('Passkey registration failed');
  const info=result.registrationInfo;
  return this.db.$transaction(async tx=>{
   if(await tx.attendanceCredential.count({where:{companyId:r.auth.companyId,employeeId:r.auth.employeeId!,status:{not:'REVOKED'}}})>=3)throw new ConflictException('Passkey limit reached');
   await tx.attendanceCredential.create({data:{id:info.credential.id,companyId:r.auth.companyId,employeeId:r.auth.employeeId!,userId:r.auth.userId,label:(challenge.payload as {label:string}).label,publicKey:Buffer.from(info.credential.publicKey),counter:BigInt(info.credential.counter),deviceType:info.credentialDeviceType,backedUp:info.credentialBackedUp}});
   await tx.audit.create({data:{companyId:r.auth.companyId,actorId:r.auth.userId,action:'attendance.passkey_registered',entityId:info.credential.id}});return {ok:true};
  },serial);
 }
 @Post('punch/options')async punchOptions(@Req()r:AuthRequest,@Body()body:unknown){
  const e=await this.employee(r),b=z.object({direction:z.enum(['IN','OUT']),location:locationInput}).parse(body);
  if(!e.branch)throw new BadRequestException('HR must assign your branch before you can punch');
  try{checkLocation(e.branch,b.location);}catch(error){throw new BadRequestException((error as Error).message);}
  const credentials=await this.db.attendanceCredential.findMany({where:{companyId:r.auth.companyId,employeeId:e.id,userId:r.auth.userId,status:'APPROVED'}});
  if(!credentials.length)throw new ForbiddenException('Register an attendance passkey and ask HR to approve it first');
  const options=await generateAuthenticationOptions({rpID:relyingParty().rpID,userVerification:'required',allowCredentials:credentials.map(c=>({id:c.id})),timeout:120000});
  const challenge=await this.issue(r,'PUNCH',options.challenge,{...b,branchId:e.branch.id});return {challengeId:challenge.id,options};
 }
 @Post('punch')async punch(@Req()r:AuthRequest,@Body()body:unknown){
  await this.employee(r);const b=envelope.parse(body),challenge=await this.consume(r,b.challengeId,'PUNCH'),c=r.auth,{origin,rpID}=relyingParty();
  const payload=z.object({direction:z.enum(['IN','OUT']),branchId:text,location:locationInput}).parse(challenge.payload);
  return this.db.$transaction(async tx=>{
   const e=await tx.employee.findFirst({where:{id:c.employeeId!,companyId:c.companyId,active:true},include:{branch:true,company:true}});
   if(!e?.branch||e.branch.id!==payload.branchId)throw new ForbiddenException('Branch assignment changed. Start again.');
   let distance:number;try{distance=checkLocation(e.branch,payload.location);}catch(error){throw new BadRequestException((error as Error).message);}
   const credential=await tx.attendanceCredential.findFirst({where:{id:b.response.id,companyId:c.companyId,employeeId:e.id,userId:c.userId,status:'APPROVED'}});
   if(!credential)throw new ForbiddenException('Attendance passkey is not approved');
   let verified;try{verified=await verifyAuthenticationResponse({response:b.response as unknown as AuthenticationResponseJSON,expectedChallenge:challenge.challenge,expectedOrigin:origin,expectedRPID:rpID,requireUserVerification:true,credential:{id:credential.id,publicKey:new Uint8Array(credential.publicKey),counter:Number(credential.counter)}});}catch{throw new BadRequestException('Phone verification failed. Start again.');}
   if(!verified.verified)throw new ForbiddenException('Phone verification is required');
   if(challenge.expiresAt.getTime()<=Date.now())throw new BadRequestException('Verification expired. Start again.');
   const now=new Date(),day=new Date(new Intl.DateTimeFormat('en-CA',{timeZone:e.company.timezone,year:'numeric',month:'2-digit',day:'2-digit'}).format(now));
   const existing=await tx.attendance.findUnique({where:{companyId_employeeId_day:{companyId:c.companyId,employeeId:e.id,day}}});
   if(payload.direction==='IN'&&existing)throw new ConflictException('Already checked in today');
   if(payload.direction==='OUT'&&(!existing||existing.checkOut))throw new ConflictException('No open attendance record');
   const row=payload.direction==='IN'?await tx.attendance.create({data:{companyId:c.companyId,employeeId:e.id,day,checkIn:now,source:'VERIFIED'}}):await tx.attendance.update({where:{id:existing!.id},data:{checkOut:now}});
   await tx.attendanceCredential.update({where:{id:credential.id},data:{counter:BigInt(verified.authenticationInfo.newCounter),backedUp:verified.authenticationInfo.credentialBackedUp}});
   await tx.attendanceProof.create({data:{companyId:c.companyId,employeeId:e.id,attendanceId:row.id,direction:payload.direction,branchId:e.branch.id,credentialId:credential.id,latitude:payload.location.latitude,longitude:payload.location.longitude,accuracyMetres:payload.location.accuracyMetres,distanceMetres:distance,capturedAt:new Date(payload.location.capturedAt)}});
   await tx.audit.create({data:{companyId:c.companyId,actorId:c.userId,action:`attendance.${payload.direction.toLowerCase()}_verified`,entityId:row.id}});return row;
  },{...serial,timeout:15000});
 }
}
