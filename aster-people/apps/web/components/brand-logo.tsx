export function BrandMark({className=''}:{className?:string}) {
 return <svg className={className} viewBox="0 0 64 64" fill="none" aria-hidden="true" focusable="false">
  <rect width="64" height="64" rx="18" fill="#183C38"/>
  <rect x="1" y="1" width="62" height="62" rx="17" stroke="#FFFFFF" strokeOpacity=".14"/>
  <path fillRule="evenodd" clipRule="evenodd" d="M17 13H31C44.807 13 54 20.71 54 32S44.807 51 31 51H17V13ZM26 22V42H31C39.563 42 45 38.188 45 32S39.563 22 31 22H26Z" fill="#F4F3E9"/>
  <path d="M10 46L27 29L34 36L17 53H10V46Z" fill="#C4D998"/>
  <path d="M27 29L34 36L37 26L27 29Z" fill="#E3C486"/>
 </svg>;
}

export default function BrandLogo(){
 return <span className="daksha-brand">
 <span className="daksha-logo" role="img" aria-label="Daksha — People. Precision. Progress.">
  <BrandMark className="daksha-logo-mark"/>
  <span className="daksha-logo-type" aria-hidden="true"><span className="daksha-logo-name">Daksha<span className="daksha-logo-period">.</span></span><span className="daksha-logo-caption">People. Precision. Progress.</span></span>
 </span>
 <span className="daksha-brand-meaning">Sanskrit for “skilled and capable.”</span>
 </span>;
}
