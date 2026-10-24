export type PayInput = {monthlyBasic:number;monthlyAllowance:number;monthlyTds:number;pfEnabled:boolean;esicEnabled:boolean};
export type PayPolicy = {pfEmployeeBps:number;pfEmployerBps:number;pfCeiling:number;esicEmployeeBps:number;esicEmployerBps:number;esicCeiling:number;professionalTax:number};
// Monetary values are paise, rates are basis points. No binary floating-point currency arithmetic.
const rate=(amount:number,bps:number)=>Number((BigInt(amount)*BigInt(bps)+5000n)/10000n);
export function calculatePay(e:PayInput,p:PayPolicy) {
 for(const n of [...Object.values(p),e.monthlyBasic,e.monthlyAllowance,e.monthlyTds]) if(!Number.isSafeInteger(n)||n<0) throw Error('Invalid monetary input');
 const gross=e.monthlyBasic+e.monthlyAllowance;
 const pfBase=e.pfEnabled?Math.min(e.monthlyBasic,p.pfCeiling):0;
 const esicBase=e.esicEnabled&&gross<=p.esicCeiling?gross:0;
 const result={basic:e.monthlyBasic,allowance:e.monthlyAllowance,pfEmployee:rate(pfBase,p.pfEmployeeBps),pfEmployer:rate(pfBase,p.pfEmployerBps),esicEmployee:rate(esicBase,p.esicEmployeeBps),esicEmployer:rate(esicBase,p.esicEmployerBps),tds:e.monthlyTds,professionalTax:p.professionalTax};
 const net=gross-result.pfEmployee-result.esicEmployee-result.tds-result.professionalTax;
 if(!Number.isSafeInteger(net)||net<0) throw Error('Deductions exceed earnings');
 return {...result,net};
}
