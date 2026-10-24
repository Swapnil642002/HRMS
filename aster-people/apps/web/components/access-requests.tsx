'use client';
import { useEffect, useState } from 'react';
import { apiFetch } from '../lib/api';
type AccessRequest={id:string;name:string;email:string;employeeCode:string;status:string;reviewNote:string};
export default function AccessRequests({company}:{company:string}){
 const [rows,setRows]=useState<AccessRequest[]>([]),[loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState(''),[configured,setConfigured]=useState(false),[notes,setNotes]=useState<Record<string,string>>({});
 async function read(path:string){return (await apiFetch(path,{headers:{'x-company-id':company}})).json();}
 async function reload(){setRows(await read('access-requests'));}
 useEffect(()=>{let current=true;setLoading(true);Promise.all([read('access-requests'),read('account-delivery')]).then(([requests,delivery])=>{if(current){setRows(requests);setConfigured(delivery.configured);}}).catch(e=>{if(current)setError(e.message);}).finally(()=>{if(current)setLoading(false);});return()=>{current=false;};},[company]);
 async function decide(id:string,decision:string){setBusy(true);setError('');setMessage('');try{
  const result=await apiFetch(`access-requests/${id}/decide`,{method:'POST',headers:{'Content-Type':'application/json','x-company-id':company},body:JSON.stringify({decision,note:notes[id]||''})});
  setMessage((await result.json()).message);await reload();
 }catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 return <section className="panel"><h2>Employee access requests</h2><p>Share this <a href={'/account?mode=request&company='+encodeURIComponent(company)}>employee registration link</a> with your team.</p><p>Workspace code: <code>{company}</code></p><p className="muted">Approval grants the Employee role and links the active employee record matching the work email and employee code. Create that record in People before approving.</p>
 {!configured&&<div className="info-note">Email delivery is not configured. Requests can be reviewed, but password emails stay queued until SMTP and the notification worker are configured.</div>}
 {error&&<div role="alert" className="alert">{error}</div>}{message&&<p role="status" className="success">{message}</p>}
 <button disabled={loading||busy} onClick={()=>{setError('');void reload().catch(e=>setError(e.message));}}>Refresh requests</button>
 {loading?<p role="status">Loading requests…</p>:rows.length?<div className="table-scroll"><table><thead><tr><th>Employee</th><th>Work email / code</th><th>Status</th><th>Review</th></tr></thead><tbody>{rows.map(r=><tr key={r.id}><td>{r.name}</td><td>{r.email}<small>{r.employeeCode}</small></td><td>{r.status}</td><td>{r.status==='PENDING'?<><label>Internal review note<input aria-label={'Review note for '+r.email} maxLength={1000} value={notes[r.id]||''} onChange={e=>setNotes({...notes,[r.id]:e.target.value})}/></label><div className="actions"><button disabled={busy} onClick={()=>void decide(r.id,'REJECT')}>Reject</button><button className="primary" disabled={busy} onClick={()=>void decide(r.id,'APPROVE')}>Approve employee access</button></div></>:r.reviewNote||'Reviewed'}</td></tr>)}</tbody></table><small>Showing up to 200 recent requests.</small></div>:<p>No access requests yet.</p>}
 </section>;
}
