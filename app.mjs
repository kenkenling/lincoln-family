import {decrypt} from './crypto.mjs';

const form=document.getElementById('unlock-form');
const input=document.getElementById('passphrase');
const button=document.getElementById('unlock');
const message=document.getElementById('message');
const welcome=document.getElementById('welcome');
const family=document.getElementById('family');
const frame=document.getElementById('calendar');
const lockButton=document.getElementById('lock');
let blobURLs=[];
let operation=0;

function lock() {
  operation++;
  frame.removeAttribute('srcdoc');
  frame.src='about:blank';
  for (const url of blobURLs) URL.revokeObjectURL(url);
  blobURLs=[];
  input.value='';message.textContent='';
  family.hidden=true;welcome.hidden=false;
  button.disabled=false;button.textContent='Open family calendar';
  input.focus();
}

function display(payload) {
  const doc=new DOMParser().parseFromString(payload.html,'text/html');
  // Only the reviewed, script-free calendar is shown; no plaintext resource fetches.
  doc.querySelectorAll('script, iframe, object, embed, base, form, link').forEach(node=>node.remove());
  const downloads={
    'lincoln-2026-2027.ics':URL.createObjectURL(new Blob([payload.ics],{type:'text/calendar;charset=utf-8'})),
    'agenda.md':URL.createObjectURL(new Blob([payload.agenda],{type:'text/markdown;charset=utf-8'})),
  };
  blobURLs=Object.values(downloads);
  for (const link of doc.querySelectorAll('a[href]')) {
    const href=link.getAttribute('href');
    if (downloads[href]) {link.href=downloads[href];link.download=href;}
    else if (!href.startsWith('#')) link.removeAttribute('href');
  }
  const policy=doc.createElement('meta');policy.httpEquiv='Content-Security-Policy';
  policy.content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'";
  doc.head.prepend(policy);
  frame.removeAttribute('src');
  frame.srcdoc='<!doctype html>'+doc.documentElement.outerHTML;
  welcome.hidden=true;family.hidden=false;
  lockButton.focus();
}

form.addEventListener('submit',async event=>{
  event.preventDefault();
  if (button.disabled) return;
  if (!globalThis.crypto?.subtle) {message.textContent='Please open the HTTPS website in a current browser.';return;}
  const token=++operation;
  button.disabled=true;button.textContent='Opening…';message.textContent='';
  let password=input.value;
  try {
    const response=await fetch('./calendar.enc.json',{cache:'no-store',credentials:'omit'});
    if (!response.ok) throw new Error('load');
    const envelope=await response.json();
    let payload;
    try {payload=await decrypt(envelope,password);}
    catch {throw new Error('unlock');}
    if (token!==operation) return;
    display(payload);
  } catch (error) {
    if (token!==operation) return;
    message.textContent=error.message==='unlock' ? 'That passphrase didn’t unlock the calendar. Check it and try again.' : 'The calendar could not be loaded. Please reload and try again.';
    input.focus();
  } finally {
    password='';input.value='';
    if (token===operation) {button.disabled=false;button.textContent='Open family calendar';}
  }
});
lockButton.addEventListener('click',lock);
frame.addEventListener('load',()=>{
  // srcdoc inherits the outer page's base URL; handle fragment links locally.
  if (!frame.hasAttribute('srcdoc')) return;
  const doc=frame.contentDocument;
  doc?.addEventListener('click',event=>{
    const link=event.target.closest?.('a[href^="#"]');
    if (!link) return;
    event.preventDefault();
    const target=doc.getElementById(link.getAttribute('href').slice(1));
    if(target?.tagName==='DETAILS') target.open=true;
    target?.scrollIntoView({block:'start'});
  });
});
window.addEventListener('pagehide',lock);
