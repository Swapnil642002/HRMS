import test from 'node:test';
import assert from 'node:assert/strict';
import { calculatePay } from '../apps/api/src/payroll';
import { hashPassword,verifyPassword } from '../apps/api/src/security';
import { days,requestInput } from '../apps/api/src/validation';
const policy={pfEmployeeBps:1200,pfEmployerBps:1200,pfCeiling:1500000,esicEmployeeBps:75,esicEmployerBps:325,esicCeiling:2100000,professionalTax:20000};
// Synthetic inputs exercise arithmetic; these are not statutory advice or seeded company policies.
test('payroll caps PF wages and excludes employer contributions from net deductions',()=>{const r=calculatePay({monthlyBasic:2000000,monthlyAllowance:500000,monthlyTds:10000,pfEnabled:true,esicEnabled:true},policy);assert.equal(r.pfEmployee,180000);assert.equal(r.pfEmployer,180000);assert.equal(r.esicEmployee,0);assert.equal(r.net,2290000);});
test('ESIC eligibility boundary is inclusive and rounding is exact',()=>{const r=calculatePay({monthlyBasic:2100000,monthlyAllowance:0,monthlyTds:0,pfEnabled:false,esicEnabled:true},policy);assert.equal(r.esicEmployee,15750);assert.equal(r.esicEmployer,68250);assert.equal(r.net,2064250);});
test('invalid and negative net payroll are rejected',()=>{assert.throws(()=>calculatePay({monthlyBasic:100,monthlyAllowance:0,monthlyTds:200,pfEnabled:false,esicEnabled:false},policy));assert.throws(()=>calculatePay({monthlyBasic:1.5,monthlyAllowance:0,monthlyTds:0,pfEnabled:false,esicEnabled:false},policy));});
test('salted passwords verify without reusable hashes',()=>{const a=hashPassword('a-long-test-password'),b=hashPassword('a-long-test-password');assert.notEqual(a,b);assert(verifyPassword('a-long-test-password',a));assert(!verifyPassword('incorrect',a));});
test('leave dates are inclusive, leap-aware and reject reverse periods',()=>{assert.equal(days('2028-02-28','2028-03-01'),3);assert.throws(()=>days('2026-10-24','2026-10-23'));});
test('expense payload cannot override tenant, status or inject extra financial fields',()=>{const r=requestInput.parse({kind:'EXPENSE',employeeId:'abc',title:'Meals',companyId:'other',status:'APPROVED',amount:100,details:{category:'MEALS',incurredOn:'2026-10-23',description:'Customer visit'}});assert(!('companyId' in r));assert(!('status' in r));assert.throws(()=>requestInput.parse({kind:'EXPENSE',employeeId:'abc',title:'Meals',amount:-1,details:{}}));});
