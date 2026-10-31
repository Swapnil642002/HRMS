import { PrismaClient } from '@prisma/client';
import { Queue, Worker } from 'bullmq';
import nodemailer from 'nodemailer';
import { validateEnv, checkRedis } from './config';
async function main(){
validateEnv(process.env,'worker');await checkRedis();
const db=new PrismaClient();await db.$connect();
const redis=new URL(process.env.REDIS_URL!);
const connection={host:redis.hostname,port:Number(redis.port||6379),username:redis.username?decodeURIComponent(redis.username):undefined,password:redis.password?decodeURIComponent(redis.password):undefined,db:Number(redis.pathname.slice(1)||0),...(redis.protocol==='rediss:'?{tls:{}}:{})};
const queue=new Queue('daksha-email',{connection});
const transport=process.env.SMTP_HOST&&process.env.MAIL_FROM?nodemailer.createTransport({host:process.env.SMTP_HOST,port:Number(process.env.SMTP_PORT||587),secure:process.env.SMTP_PORT==='465',requireTLS:true,auth:process.env.SMTP_USER?{user:process.env.SMTP_USER,pass:process.env.SMTP_PASSWORD}:undefined,disableFileAccess:true,disableUrlAccess:true}):null;
const worker=new Worker('daksha-email',async job=>{if(!transport)throw Error('SMTP is not configured');const message=await db.outbox.findUnique({where:{id:job.data.id}});if(!message||message.sentAt)return;await transport.sendMail({from:process.env.MAIL_FROM,to:message.recipient,subject:message.subject,text:message.body,messageId:`<${message.id}@${process.env.MAIL_MESSAGE_DOMAIN||'daksha.internal'}>`});await db.outbox.update({where:{id:message.id},data:{sentAt:new Date(),body:"[Delivered]"}});},{connection,concurrency:2});
worker.on('failed',(job,error)=>console.error('Notification failed',job?.id,error.message));
let running=false;const poll=async()=>{if(running||!transport)return;running=true;try{const messages=await db.outbox.findMany({where:{sentAt:null},take:100,orderBy:{createdAt:'asc'}});for(const m of messages)await queue.add('send',{id:m.id},{jobId:m.id,attempts:5,backoff:{type:'exponential',delay:30000},removeOnComplete:{age:86400},removeOnFail:{age:86400}});}catch(e){console.error(e);}finally{running=false;}};
const timer=setInterval(()=>void poll(),10000);void poll();
async function shutdown(){clearInterval(timer);await worker.close();await queue.close();await db.$disconnect();process.exit(0);}process.on('SIGTERM',shutdown);process.on('SIGINT',shutdown);
console.log(transport?'Notification worker running':'Notification worker idle: SMTP not configured; outbox retained');

}
void main().catch(()=>{console.error('Worker startup failed; verify configuration and dependency availability');process.exitCode=1;});
