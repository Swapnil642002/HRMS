'use client';
import { useEffect, useState } from 'react';
import { apiFetch } from '../lib/api';
type Employee={id:string;name:string;code:string;active:boolean;reportingManagerId?:string|null;reportingManager?:{user:{name:string}}|null};
type Manager={id:string;user:{name:string};role:string};
export default function ReportingManagers({company,employees,onSaved}:{company:string;employees:Employee[];onSaved:()=>Promise<void>}){
 const [managers,setManagers]=useState<Manager[]>([]),[employeeId,setEmployeeId]=useState(''),[managerId,setManagerId]=useState(''),[loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[error,setError]=useState('');
 useEffect(()=>{let current=true;apiFetch('requests/managers',{headers:{'x-company-id':company}}).then(r=>r.json()).then(rows=>{if(current)setManagers(rows);}).catch(e=>{if(current)setError(e.message);}).finally(()=>{if(current)setLoading(false);});return()=>{current=false;};},[company]);
 const active=employees.filter(e=>e.active),employee=active.find(e=>e.id===employeeId);
 return <details className="reporting-manager-settings"><summary>Assign reporting managers · HR / Admin</summary><p className="muted">Assign an employee’s first approver. New requests go to this manager automatically. Changing the assignment also redirects requests still waiting for manager approval.</p>
 {error&&<div role="alert" className="alert">{error}</div>}
 {!loading&&!managers.length&&<p className="info-note">No eligible manager accounts. An administrator must create an active Manager, HR or Admin account in this company first.</p>}
 {!active.length?<p>Add an active employee in People before assigning a manager.</p>:<form className="attendance-form" onSubmit={async e=>{e.preventDefault();setBusy(true);setError('');try{await apiFetch('requests/assign-manager',{method:'POST',headers:{'Content-Type':'application/json','x-company-id':company},body:JSON.stringify({employeeId,managerId})});await onSaved();}catch(err){setError((err as Error).message);}finally{setBusy(false);}}}>
 <label>Employee<select required value={employeeId} disabled={busy||loading} onChange={e=>{setEmployeeId(e.target.value);setManagerId(active.find(p=>p.id===e.target.value)?.reportingManagerId||'');}}><option value="">Select employee</option>{active.map(e=><option key={e.id} value={e.id}>{e.name} · {e.code}</option>)}</select></label>
 <label>Reporting manager<select required value={managerId} disabled={busy||loading} onChange={e=>setManagerId(e.target.value)}><option value="">Select manager</option>{managers.map(m=><option key={m.id} value={m.id}>{m.user.name} · {m.role.toLowerCase()}</option>)}</select></label>
 <button className="primary" disabled={busy||loading||!employeeId||!managerId}>{busy?'Saving…':'Assign manager'}</button>
 </form>}
 {employee&&<p className="muted">Current manager: {employee.reportingManager?.user.name||'Not assigned'}</p>}
 </details>;
}
