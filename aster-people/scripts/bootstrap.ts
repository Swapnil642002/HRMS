import { PrismaClient, Prisma } from '@prisma/client';
import { hashPassword } from '../apps/api/src/security';
import { validateEnv } from '../apps/api/src/config';
import { bootstrapIdentity } from '../apps/api/src/bootstrap-config';
async function main(){
 validateEnv(process.env,'bootstrap');
 const identity=bootstrapIdentity(process.env);
 delete process.env.ADMIN_PASSWORD;
 const passwordHash=hashPassword(identity.password); identity.password='';
 const db=new PrismaClient();
 try { await db.$transaction(async tx=>{
  // Serialize concurrent one-time bootstrap attempts before checking for existing users.
  await tx.$executeRaw`LOCK TABLE "User" IN EXCLUSIVE MODE`;
  if(await tx.user.count())throw Error('Bootstrap is disabled once a user exists');
  const user=await tx.user.create({data:{email:identity.email,name:identity.name,passwordHash}});
  const company=await tx.company.create({data:{name:identity.company,memberships:{create:{userId:user.id,role:'ADMIN'}}}});
  await tx.audit.create({data:{companyId:company.id,actorId:user.id,action:'organization.bootstrapped',entityId:company.id}});
 },{isolationLevel:Prisma.TransactionIsolationLevel.ReadCommitted});
 console.log('Organization and administrator created.');
 } finally {await db.$disconnect();}
}
void main().catch(()=>{console.error('Bootstrap refused or failed. Verify required inputs and that the database has no users.');process.exitCode=1;});
