import 'reflect-metadata';
import { Module, Catch, ArgumentsHost, ExceptionFilter, HttpException } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { Prisma } from '@prisma/client';
import { ZodError } from 'zod';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { validateEnv, checkRedis } from './config';
import { configureHttp } from './http-config';
import { AttendanceController } from './attendance';
import { AccountsController } from './accounts';
import { Db } from './db';
import { AuthGuard } from './auth';
import { AuthController, PeopleController, WorkflowController, PayrollController, TalentController, IntegrationController } from './routes';
@Catch()
class Errors implements ExceptionFilter {catch(e:unknown,host:ArgumentsHost){
 const res=host.switchToHttp().getResponse();
 if(e instanceof ZodError)return res.status(400).json({message:e.issues.map(i=>`${i.path.join('.')}: ${i.message}`).join('; ')});
 if(e instanceof HttpException)return res.status(e.getStatus()).json({message:e.message});
 if(e instanceof Prisma.PrismaClientKnownRequestError){const conflict=['P2002','P2034'].includes(e.code);return res.status(conflict?409:400).json({message:conflict?'Record changed or already exists. Refresh and retry.':'The operation violates a record constraint.'});}
 console.error(e);res.status(500).json({message:'Unable to complete this operation.'});
}}
@Module({providers:[Db,AuthGuard],controllers:[AttendanceController,AccountsController,AuthController,PeopleController,WorkflowController,PayrollController,TalentController,IntegrationController]})class App{}
export async function createApp(database?: Db) {
 const module = database ? {module:App, providers:[{provide:Db,useValue:database}]} : App;
 const app=await NestFactory.create<NestExpressApplication>(module,{rawBody:true,logger:false});
 configureHttp(app);app.useGlobalFilters(new Errors());app.enableShutdownHooks();return app;
}
async function main(){
 validateEnv(); await checkRedis();
 const app=await createApp();await app.listen(Number(process.env.PORT||4000),process.env.HOST||'127.0.0.1');
}
if (require.main === module) void main().catch(() => {console.error('API startup failed; verify configuration and dependency availability');process.exitCode=1;});
