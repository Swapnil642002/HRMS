import fs from 'node:fs/promises';
const packages = {'next':'16','react':'19','react-dom':'19','@nestjs/common':'11','@nestjs/core':'11','@nestjs/platform-express':'11','@prisma/client':'6','prisma':'6','reflect-metadata':'0','rxjs':'7','zod':'4','helmet':'8','bullmq':'5','ioredis':'5','nodemailer':'10','deepmerge-ts':'8','pdf-lib':'1','@aws-sdk/client-s3':'3','@aws-sdk/s3-request-presigner':'3','typescript':'5','tsx':'4','@types/node':'24','@types/react':'19','@types/react-dom':'19','@types/express':'5','@types/nodemailer':'7','tailwindcss':'4','@tailwindcss/postcss':'4'};
const reviewed = [];
for (const [name,major] of Object.entries(packages)) {
 const response=await fetch(`https://registry.npmjs.org/${encodeURIComponent(name)}`); if(!response.ok) throw Error(name);
 const data=await response.json(); const versions=Object.keys(data.versions).filter(v=>v.startsWith(major+'.')&&!v.includes('-')).sort((a,b)=>a.localeCompare(b,undefined,{numeric:true}));
 const pkg=data.versions[versions.at(-1)];
 if(!['MIT','MIT-0','Apache-2.0','BSD-3-Clause','BSD-2-Clause','ISC','0BSD'].includes(pkg.license)) throw Error(`License review required: ${name}: ${pkg.license}`);
 reviewed.push({name,version:pkg.version,license:pkg.license,source:`https://registry.npmjs.org/${encodeURIComponent(name)}/${pkg.version}`});
}
await fs.writeFile('docs/direct-dependencies.json',JSON.stringify({reviewedAt:new Date().toISOString(),basis:'Published package license declarations; permissive licenses allow private use and distribution subject to notices. Full installed-tree notice inventory is generated separately.',packages:reviewed},null,2));
console.log(reviewed);
