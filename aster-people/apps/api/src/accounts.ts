import { Body, Controller, Get, Post, Param, Req, Res, UseGuards, BadRequestException, ConflictException, NotFoundException, UnauthorizedException, HttpException, OnModuleDestroy } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { randomBytes } from 'node:crypto';
import type { Request, Response } from 'express';
import Redis from 'ioredis';
import { z } from 'zod';
import { Db } from './db';
import { AuthGuard, AuthRequest, requireRole } from './auth';
import { cookieName, digest, hashPassword, tokenFromCookie, verifyPassword } from './security';

const emailInput=z.string().trim().max(200).toLowerCase().pipe(z.email());
const passwordInput=z.string().min(14).max(256);
const serial={isolationLevel:Prisma.TransactionIsolationLevel.Serializable};
const acknowledgement={message:'If the details are eligible, you will receive an email with the next steps.'};

async function passwordEmail(tx:Prisma.TransactionClient,user:{id:string;email:string},invitation=false){
  const token=randomBytes(32).toString('hex');
  // Only hashes are stored in the token table; the link is retained in the email outbox until sent.
  await tx.passwordToken.updateMany({where:{userId:user.id,usedAt:null},data:{usedAt:new Date()}});
  await tx.passwordToken.create({data:{id:digest(token),userId:user.id,expiresAt:new Date(Date.now()+60*60*1000)}});
  const link=new URL('/account',process.env.APP_ORIGIN!);link.hash='token='+token;
  await tx.outbox.create({data:{recipient:user.email,subject:invitation?'Your Daksha access is approved':'Reset your Daksha password',body:`${invitation?'Your administrator approved your access. Set your password':'Reset your password'} using this link:\n\n${link}\n\nThis link expires in one hour and can be used once. If it expires, use Forgot password on the sign-in page. If you did not request this, contact your administrator.`}});
}

@Controller()
export class AccountsController implements OnModuleDestroy {
  private redis=new Redis(process.env.REDIS_URL||'redis://localhost:6379',{lazyConnect:true,maxRetriesPerRequest:1});
  constructor(private db:Db){}
  onModuleDestroy(){this.redis.disconnect();}
  private async limit(req:Request,action:string,email?:string){
    for(const key of [`account:${action}:ip:${req.ip}`,...(email?[`account:${action}:email:${digest(email)}`]:[])]){
      const count=Number(await this.redis.eval("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],900) end; return n",1,key));
      if(count>10)throw new HttpException('Too many attempts. Try again in 15 minutes.',429);
    }
  }
  @Post('auth/request-access')
  async requestAccess(@Body()body:unknown,@Req()req:Request){
    const b=z.object({companyId:z.string().min(1).max(200),employeeCode:z.string().trim().min(1).max(200),name:z.string().trim().min(1).max(200),email:emailInput}).strict().parse(body);
    await this.limit(req,'access',b.email);
    if(!await this.db.company.findUnique({where:{id:b.companyId}}))return {message:'Your request has been submitted for administrator review. If approved, you will receive an email.'};
    // Repeated requests do not overwrite an administrator's decision or an existing pending request.
    await this.db.accessRequest.upsert({where:{companyId_email:{companyId:b.companyId,email:b.email}},create:b,update:{}});
    return {message:'Your request has been submitted for administrator review. If approved, you will receive an email.'};
  }
  @Post('auth/forgot-password')
  async forgot(@Body()body:unknown,@Req()req:Request){
    const {email}=z.object({email:emailInput}).parse(body);await this.limit(req,'reset',email);
    await this.db.$transaction(async tx=>{const user=await tx.user.findUnique({where:{email}});if(user?.active)await passwordEmail(tx,user);},serial);
    return acknowledgement;
  }
  @Post('auth/reset-password')
  async reset(@Body()body:unknown,@Req()req:Request,@Res({passthrough:true})res:Response){
    const b=z.object({token:z.string().regex(/^[a-f0-9]{64}$/),password:passwordInput}).strict().parse(body);
    await this.limit(req,'consume');
    await this.db.$transaction(async tx=>{
      const token=await tx.passwordToken.findUnique({where:{id:digest(b.token)},include:{user:true}});
      if(!token||token.usedAt||token.expiresAt<=new Date()||!token.user.active)throw new BadRequestException('This link is invalid or expired. Request a new password reset.');
      const consumed=await tx.passwordToken.updateMany({where:{id:token.id,usedAt:null,expiresAt:{gt:new Date()}},data:{usedAt:new Date()}});
      if(!consumed.count)throw new BadRequestException('This link has already been used.');
      await tx.user.update({where:{id:token.userId},data:{passwordHash:hashPassword(b.password),passwordSet:true}});
      await tx.passwordToken.updateMany({where:{userId:token.userId,usedAt:null},data:{usedAt:new Date()}});
      await tx.session.deleteMany({where:{userId:token.userId}});
    },serial);
    res.clearCookie(cookieName,{path:'/'});return {message:'Your password has been saved. Sign in with your new password.'};
  }
  @Post('auth/change-password')
  async change(@Body()body:unknown,@Req()req:Request,@Res({passthrough:true})res:Response){
    const b=z.object({currentPassword:z.string().min(1).max(256),password:passwordInput}).strict().parse(body);
    await this.limit(req,'change');
    const raw=tokenFromCookie(req.headers.cookie);if(!raw)throw new UnauthorizedException();
    await this.db.$transaction(async tx=>{
      const session=await tx.session.findUnique({where:{id:digest(raw)},include:{user:true}});
      if(!session||session.expiresAt<=new Date()||!session.user.active)throw new UnauthorizedException();
      if(!verifyPassword(b.currentPassword,session.user.passwordHash))throw new BadRequestException('Current password is incorrect.');
      if(b.currentPassword===b.password)throw new BadRequestException('Choose a different password.');
      await tx.user.update({where:{id:session.userId},data:{passwordHash:hashPassword(b.password),passwordSet:true}});
      await tx.passwordToken.updateMany({where:{userId:session.userId,usedAt:null},data:{usedAt:new Date()}});
      await tx.session.deleteMany({where:{userId:session.userId}});
    },serial);
    res.clearCookie(cookieName,{path:'/'});return {message:'Password changed. Sign in again on each device.'};
  }
  @UseGuards(AuthGuard) @Get('access-requests')
  async requests(@Req()req:AuthRequest){
    requireRole(req.auth,'ADMIN');
    return this.db.accessRequest.findMany({where:{companyId:req.auth.companyId},orderBy:{createdAt:'desc'},take:200});
  }
  @UseGuards(AuthGuard) @Post('access-requests/:id/decide')
  async decide(@Req()req:AuthRequest,@Param('id')id:string,@Body()body:unknown){
    const c=req.auth;requireRole(c,'ADMIN');
    const b=z.object({decision:z.enum(['APPROVE','REJECT']),note:z.string().trim().max(1000).default('')}).strict().parse(body);
    return this.db.$transaction(async tx=>{
      const request=await tx.accessRequest.findFirst({where:{id,companyId:c.companyId}});
      if(!request)throw new NotFoundException();
      if(request.status!=='PENDING')throw new ConflictException('This request has already been reviewed.');
      if(b.decision==='APPROVE'){
        const employee=await tx.employee.findFirst({where:{companyId:c.companyId,code:request.employeeCode,email:request.email,active:true,membershipId:null}});
        if(!employee)throw new BadRequestException('Create or correct the active employee record first. Employee code and work email must match, and the record must not already have an account.');
        let user=await tx.user.findUnique({where:{email:request.email}});
        if(user&&!user.active)throw new BadRequestException('This account is disabled.');
        if(!user)user=await tx.user.create({data:{email:request.email,name:employee.name,passwordHash:hashPassword(randomBytes(32).toString('hex')),passwordSet:false}});
        if(await tx.membership.findUnique({where:{userId_companyId:{userId:user.id,companyId:c.companyId}}}))throw new ConflictException('This person already has access.');
        const membership=await tx.membership.create({data:{userId:user.id,companyId:c.companyId,role:'EMPLOYEE'}});
        await tx.employee.update({where:{id:employee.id},data:{membershipId:membership.id}});
        if(!user.passwordSet)await passwordEmail(tx,user,true);
        else await tx.outbox.create({data:{recipient:user.email,subject:'Your Daksha access is approved',body:`Your employee access has been approved. Sign in with your existing password at ${process.env.APP_ORIGIN}.`}});
      }else await tx.outbox.create({data:{recipient:request.email,subject:'Your Daksha access request was reviewed',body:'Your access request was not approved. Contact your organization administrator for help.'}});
      await tx.accessRequest.update({where:{id},data:{status:b.decision==='APPROVE'?'APPROVED':'REJECTED',reviewNote:b.note,reviewedBy:c.userId,reviewedAt:new Date()}});
      await tx.audit.create({data:{companyId:c.companyId,actorId:c.userId,action:`access.${b.decision.toLowerCase()}`,entityId:id}});
      return {message:b.decision==='APPROVE'?'Employee access approved. An email has been queued.':'Access request rejected.'};
    },serial);
  }
  @UseGuards(AuthGuard) @Get('account-delivery')
  async delivery(@Req()req:AuthRequest){requireRole(req.auth,'ADMIN');return {configured:Boolean(process.env.SMTP_HOST&&process.env.MAIL_FROM)};}
}
