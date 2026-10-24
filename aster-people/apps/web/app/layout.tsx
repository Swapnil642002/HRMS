import type { Metadata } from 'next';
import './globals.css';
export const metadata:Metadata={title:'Daksha | Micromap',description:'People, precision and progress. Micromap workforce operations.'};
export default function Layout({children}:{children:React.ReactNode}){return <html lang="en"><body>{children}</body></html>;}
