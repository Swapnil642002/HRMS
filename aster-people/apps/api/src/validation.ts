import { z } from 'zod';
export const text=z.string().trim().min(1).max(200);
export const money=z.number().int().min(0).max(100000000);
export const date=z.iso.date();
export const employeeInput=z.object({code:text,name:text,email:z.email(),department:text,designation:text,joinedOn:date,monthlyBasic:money,monthlyAllowance:money.default(0),monthlyTds:money.default(0),pfEnabled:z.boolean().default(true),esicEnabled:z.boolean().default(false),leaveAllowance:z.number().int().min(0).max(366).default(24)}).strict();
export const requestInput=z.discriminatedUnion('kind',[
 z.object({kind:z.literal('LEAVE'),employeeId:text,title:text,details:z.object({from:date,to:date,reason:text}).strict()}),
 z.object({kind:z.literal('TRAVEL'),employeeId:text,title:text,amount:money,details:z.object({from:date,to:date,destination:text,purpose:text}).strict()}),
 z.object({kind:z.literal('EXPENSE'),employeeId:text,title:text,amount:money,details:z.object({category:z.enum(['TRAVEL','MEALS','OFFICE','OTHER']),incurredOn:date,description:text,travelId:text.optional()}).strict()}),
 z.object({kind:z.literal('ADVANCE'),employeeId:text,title:text,amount:money,details:z.object({reason:text}).strict()}),
 z.object({kind:z.literal('LOAN'),employeeId:text,title:text,amount:money,details:z.object({reason:text,installments:z.number().int().min(1).max(120)}).strict()})
]);
export const policyInput=z.object({name:text,effectiveFrom:date,pfEmployeeBps:z.number().int().min(0).max(10000),pfEmployerBps:z.number().int().min(0).max(10000),pfCeiling:money,esicEmployeeBps:z.number().int().min(0).max(10000),esicEmployerBps:z.number().int().min(0).max(10000),esicCeiling:money,professionalTax:money,confirmedReviewed:z.literal(true)}).strict();
export function days(from:string,to:string){const count=(Date.parse(to)-Date.parse(from))/86400000+1;if(!Number.isInteger(count)||count<1||count>366)throw Error('Date range must be between 1 and 366 calendar days');return count;}
