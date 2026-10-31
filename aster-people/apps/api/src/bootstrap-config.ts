export function bootstrapIdentity(env: NodeJS.ProcessEnv) {
 const email=env.ADMIN_EMAIL?.trim().toLowerCase(),password=env.ADMIN_PASSWORD;
 if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || !password || password.length<14 || password.length>256) throw new Error('Supply valid ADMIN_EMAIL and ADMIN_PASSWORD (14–256 characters)');
 const domain=email.split('@')[1];
 if (/(^|[.-])(demo|example|test|daksha|aster)([.-]|$)/i.test(domain) || /\.(local|localhost|invalid|test|example)$/.test(domain)) throw new Error('Demo administrator identities are prohibited');
 if (!env.ADMIN_NAME?.trim() || !env.COMPANY_NAME?.trim()) throw new Error('ADMIN_NAME and COMPANY_NAME are required');
 return {email,password,name:env.ADMIN_NAME,company:env.COMPANY_NAME};
}
