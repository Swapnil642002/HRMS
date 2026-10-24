import test from 'node:test';
import assert from 'node:assert/strict';
import { checkLocation, distanceMetres, locationInput } from '../apps/api/src/attendance-policy';
const now=Date.now();
const branch={latitude:19.076,longitude:72.8777,active:true,radiusMetres:50,maxAccuracyMetres:25};
const reading={latitude:branch.latitude,longitude:branch.longitude,accuracyMetres:10,capturedAt:now};
test('assigned branch centre is accepted, other cities are rejected',()=>{
 assert.equal(checkLocation(branch,reading,now),0);
 assert.throws(()=>checkLocation(branch,{...reading,latitude:28.6139,longitude:77.209},now),/within 50 metres/);
});
test('accuracy uncertainty must fit inside the full 50 metre boundary',()=>{
 const north=(metres:number)=>({...reading,latitude:branch.latitude+metres/6371000*180/Math.PI});
 assert.ok(checkLocation(branch,north(39),now)>38);
 assert.throws(()=>checkLocation(branch,north(41),now),/within 50 metres/);
 assert.throws(()=>checkLocation(branch,north(51),now),/within 50 metres/);
});
test('stale, future, inaccurate, inactive and malformed readings fail closed',()=>{
 assert.throws(()=>checkLocation(branch,{...reading,capturedAt:now-120001},now),/expired/);
 assert.throws(()=>checkLocation(branch,{...reading,capturedAt:now+5001},now),/expired/);
 assert.throws(()=>checkLocation(branch,{...reading,accuracyMetres:26},now));
 assert.throws(()=>checkLocation({...branch,active:false},reading,now),/inactive/);
 for(const invalid of [{latitude:NaN},{longitude:181},{latitude:91},{accuracyMetres:0},{accuracyMetres:-1},{capturedAt:Infinity}])assert.equal(locationInput.safeParse({...reading,...invalid}).success,false);
});
test('distance handles longitude wrap and poles without NaN',()=>{
 assert.ok(distanceMetres({latitude:0,longitude:179.9999},{latitude:0,longitude:-179.9999})<25);
 assert.ok(Number.isFinite(distanceMetres({latitude:90,longitude:0},{latitude:-90,longitude:180})));
});
