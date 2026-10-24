import { z } from 'zod';
export const locationInput = z.object({
 latitude:z.number().min(-90).max(90), longitude:z.number().min(-180).max(180),
 accuracyMetres:z.number().positive().max(25), capturedAt:z.number().int().positive(),
});
export type LocationReading=z.infer<typeof locationInput>;
export function distanceMetres(a:{latitude:number;longitude:number},b:{latitude:number;longitude:number}){
 const radians=(n:number)=>n*Math.PI/180;
 const dLat=radians(b.latitude-a.latitude),dLon=radians(b.longitude-a.longitude);
 const h=Math.sin(dLat/2)**2+Math.cos(radians(a.latitude))*Math.cos(radians(b.latitude))*Math.sin(dLon/2)**2;
 return 6371000*2*Math.atan2(Math.sqrt(Math.min(1,h)),Math.sqrt(Math.max(0,1-h)));
}
export function checkLocation(branch:{latitude:number;longitude:number;radiusMetres:number;maxAccuracyMetres:number;active:boolean},reading:LocationReading,now=Date.now()){
 locationInput.parse(reading);
 if(!branch.active)throw Error('Your assigned branch is inactive. Contact HR.');
 if(reading.capturedAt>now+5000||now-reading.capturedAt>120000)throw Error('Location expired. Capture your location again.');
 if(reading.accuracyMetres>branch.maxAccuracyMetres)throw Error('Location accuracy is too low. Move to a clearer area and retry.');
 const distance=distanceMetres(branch,reading);
 if(distance+reading.accuracyMetres>branch.radiusMetres)throw Error('Your location must be confidently within 50 metres of your assigned branch. Move closer and retry.');
 return distance;
}
