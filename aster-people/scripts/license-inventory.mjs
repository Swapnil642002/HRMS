import fs from 'node:fs/promises';
import path from 'node:path';
const lock=JSON.parse(await fs.readFile('package-lock.json','utf8'));
const inventory=[];const notices=[];const unresolved=[];
for(const [location,meta] of Object.entries(lock.packages)){
 if(!location.includes('node_modules/')||meta.link)continue;
 const name=location.split('node_modules/').at(-1);
 let pkg;try{pkg=JSON.parse(await fs.readFile(path.join(location,'package.json'),'utf8'));}catch{inventory.push({name,version:meta.version,license:meta.license,installed:false,resolved:meta.resolved,integrity:meta.integrity});continue;}
 const files=(await fs.readdir(location)).filter(f=>/^(licen[sc]e|copying|notice|copyright)(\.|$)/i.test(f));
 inventory.push({name,version:pkg.version,license:pkg.license??meta.license,installed:true,resolved:meta.resolved,integrity:meta.integrity,noticeFiles:files});
 if(!pkg.license&&!meta.license&&!files.length)unresolved.push(name);
 for(const f of files){try{const text=await fs.readFile(path.join(location,f),'utf8');notices.push(`\n===== ${name}@${pkg.version} : ${f} =====\n${text}`);}catch{}}
}
await fs.writeFile('docs/dependency-inventory.json',JSON.stringify(inventory,null,2)+'\n');
await fs.writeFile('docs/THIRD_PARTY_NOTICES.txt','Third-party notices. These components retain their original copyright and license.\n'+notices.join('\n'));
if(unresolved.length)throw Error('Unresolved licenses: '+unresolved.join(', '));
console.log(`Recorded ${inventory.length} locked packages and ${notices.length} notice files.`);
