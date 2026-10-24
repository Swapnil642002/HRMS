import { CanActivate, ExecutionContext, Injectable, UnauthorizedException, ForbiddenException } from '@nestjs/common';
import type { Request } from 'express';
import { Role } from '@prisma/client';
import { Db } from './db';
import { digest, tokenFromCookie } from './security';
export type Context={userId:string;companyId:string;role:Role;employeeId:string|null};
export type AuthRequest=Request & {auth:Context};
@Injectable()
export class AuthGuard implements CanActivate {
 constructor(private db:Db){}
 async canActivate(ctx:ExecutionContext){
  const req=ctx.switchToHttp().getRequest<AuthRequest>();
  const token=tokenFromCookie(req.headers.cookie); if(!token)throw new UnauthorizedException();
  const session=await this.db.session.findUnique({where:{id:digest(token)},include:{user:true}});
  if(!session||session.expiresAt<new Date()||!session.user.active)throw new UnauthorizedException();
  const companyId=req.headers['x-company-id']; if(typeof companyId!=='string')throw new ForbiddenException('Select a company');
  const member=await this.db.membership.findUnique({where:{userId_companyId:{userId:session.userId,companyId}},include:{employee:true}});
  if(!member)throw new ForbiddenException();
  req.auth={userId:session.userId,companyId,role:member.role,employeeId:member.employee?.id??null};return true;
 }
}
export function requireRole(c:Context,...roles:Role[]){if(!roles.includes(c.role))throw new ForbiddenException('Your role cannot perform this action');}
export function ownOrStaff(c:Context,employeeId:string,...staff:Role[]){if(c.employeeId!==employeeId&&!staff.includes(c.role))throw new ForbiddenException();}
export function employeeFilter(c:Context,staff:Role[]=['ADMIN','HR','FINANCE']){return staff.includes(c.role)?{}:{employeeId:c.employeeId??'__none__'};}
