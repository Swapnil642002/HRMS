'use client';
import { useEffect, useState, type FormEvent } from 'react';
import { apiFetch } from '../../lib/api';
import BrandLogo from '../../components/brand-logo';

type Mode='request'|'forgot'|'reset'|'change';
const labels:Record<Mode,string>={request:'Request employee access',forgot:'Forgot your password?',reset:'Set your password',change:'Change your password'};
export default function AccountPage(){
 const [mode,setMode]=useState<Mode>('forgot'),[company,setCompany]=useState(''),[token,setToken]=useState(''),[ready,setReady]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
 useEffect(()=>{
  const query=new URLSearchParams(location.search),fragment=new URLSearchParams(location.hash.slice(1));
  const value=fragment.get('token');
  if(value){setToken(value);setMode('reset');history.replaceState(null,'',location.pathname+'?mode=reset');}
  else {const requested=query.get('mode');if(['request','change','reset'].includes(requested||''))setMode(requested as Mode);}
  setCompany(query.get('company')||'');setReady(true);
 },[]);
 async function submit(event:FormEvent<HTMLFormElement>){
  event.preventDefault();const form=new FormData(event.currentTarget);setError('');setBusy(true);
  try{
   let path:string,body:Record<string,unknown>;
   if(mode==='request'){path='auth/request-access';body={companyId:company.trim(),employeeCode:form.get('employeeCode'),name:form.get('name'),email:form.get('email')};}
   else if(mode==='forgot'){path='auth/forgot-password';body={email:form.get('email')};}
   else{
    if(form.get('password')!==form.get('confirmation'))throw Error('The new passwords do not match.');
    path=mode==='reset'?'auth/reset-password':'auth/change-password';
    body={password:form.get('password'),...(mode==='reset'?{token}:{currentPassword:form.get('currentPassword')})};
   }
   const response=await apiFetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
   setMessage((await response.json()).message);setToken('');
  }catch(e){setError((e as Error).message);}finally{setBusy(false);}
 }
 return <main className="account-page"><section className="panel account-card"><a href="/" className="brand-home-link" aria-label="Daksha home"><BrandLogo/></a><h1>{labels[mode]}</h1>
 {!ready?<p role="status">Loading…</p>:message?<div role="status"><p>{message}</p><a href="/">Return to sign in</a></div>:<>
 {mode==='request'&&<p className="muted">Use the workspace code shared by your administrator and the email and employee code from your employee record. An administrator must approve your request.</p>}
 {mode==='forgot'&&<p className="muted">Enter your work email to receive a link. The link expires after one hour.</p>}
 {mode==='change'&&<p className="muted">Changing your password signs you out on all devices.</p>}
 {error&&<div role="alert" className="alert">{error}</div>}
 {mode==='reset'&&!token?<p>This password link is missing. <a href="/account?mode=forgot">Request a new link</a>.</p>:<form onSubmit={submit} className="account-form">
 {mode==='request'&&<><label>Workspace code<input value={company} onChange={e=>setCompany(e.target.value)} required maxLength={200}/></label><label>Employee code<input name="employeeCode" required maxLength={200}/></label><label>Full name<input name="name" autoComplete="name" required maxLength={200}/></label></>}
 {(mode==='request'||mode==='forgot')&&<label>Work email<input name="email" type="email" autoComplete="email" required maxLength={200}/></label>}
 {mode==='change'&&<label>Current password<input name="currentPassword" type="password" autoComplete="current-password" required maxLength={256}/></label>}
 {(mode==='reset'||mode==='change')&&<><label>New password<input name="password" type="password" autoComplete="new-password" minLength={14} maxLength={256} required/><small>Use at least 14 characters.</small></label><label>Confirm new password<input name="confirmation" type="password" autoComplete="new-password" minLength={14} maxLength={256} required/></label></>}
 <button className="primary" disabled={busy}>{busy?'Submitting…':mode==='request'?'Request access':mode==='forgot'?'Send reset link':'Save password'}</button>
 </form>}
 <p className="footnote"><a href="/">Back to workspace</a>{mode==='change'&&<> · <a href="/account?mode=forgot">Forgot password?</a></>}</p>
 </>}
 </section></main>;
}
