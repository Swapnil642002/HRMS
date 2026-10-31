import type { NestExpressApplication } from '@nestjs/platform-express';
import { RequestMethod } from '@nestjs/common';
import helmet from 'helmet';

export function configureHttp(app: NestExpressApplication) {
  app.set('trust proxy', process.env.TRUST_PROXY || false);
  app.setGlobalPrefix('api', {exclude:[{path:'health', method:RequestMethod.GET},{path:'api/health', method:RequestMethod.GET}]});
  app.use(helmet());
  app.enableCors({origin:process.env.APP_ORIGIN!, credentials:true, methods:['GET','HEAD','POST','PATCH','OPTIONS'], allowedHeaders:['Content-Type','x-company-id']});
  app.use((req:any,res:any,next:()=>void)=>{
    if (!['GET','HEAD','OPTIONS'].includes(req.method) && !req.path.startsWith('/api/biometric/') && req.headers.origin !== process.env.APP_ORIGIN) {
      return res.status(403).json({message:'Invalid request origin'});
    }
    next();
  });
}
