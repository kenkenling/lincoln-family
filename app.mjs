import {decrypt} from './crypto.mjs';

const form=document.getElementById('unlock-form');
const input=document.getElementById('passphrase');
const button=document.getElementById('unlock');
const message=document.getElementById('message');
const welcome=document.getElementById('welcome');
const family=document.getElementById('family');
const calendarFrame=document.getElementById('calendar');
const bookFrame=document.getElementById('book-reader');
const bookTitle=document.getElementById('book-title');
const bookSubtitle=document.getElementById('book-subtitle');
const bookEdition=document.getElementById('book-edition');
const openBook=document.getElementById('book-open');
const downloadBook=document.getElementById('book-download');
const bookView=document.getElementById('book-view');
const calendarView=document.getElementById('calendar-view');
const viewButtons=[...document.querySelectorAll('[data-view]')];
const lockButton=document.getElementById('lock');
let blobURLs=[];
let operation=0;

function decodeBase64(text) {
  const binary=atob(text);
  const bytes=new Uint8Array(binary.length);
  for(let index=0;index<binary.length;index++) bytes[index]=binary.charCodeAt(index);
  return bytes;
}

function showView(name,{focus=false}={}) {
  const bookSelected=name==='book';
  bookView.hidden=!bookSelected;
  calendarView.hidden=bookSelected;
  for(const viewButton of viewButtons){
    const selected=viewButton.dataset.view===name;
    viewButton.setAttribute('aria-pressed',String(selected));
  }
  if(focus) (bookSelected ? bookTitle : calendarFrame).focus();
}

function lock() {
  operation++;
  calendarFrame.removeAttribute('srcdoc');
  calendarFrame.src='about:blank';
  bookFrame.src='about:blank';
  openBook.removeAttribute('href');
  downloadBook.removeAttribute('href');
  for(const url of blobURLs) URL.revokeObjectURL(url);
  blobURLs=[];
  input.value='';
  message.textContent='';
  family.hidden=true;
  welcome.hidden=false;
  showView('book');
  button.disabled=false;
  button.textContent='Open family library';
  input.focus();
}

function display(payload) {
  const bookURL=URL.createObjectURL(new Blob(
    [decodeBase64(payload.book.pdfBase64)],
    {type:'application/pdf'},
  ));
  const downloads={
    'lincoln-2026-2027.ics':URL.createObjectURL(new Blob(
      [payload.calendar.ics],
      {type:'text/calendar;charset=utf-8'},
    )),
    'agenda.md':URL.createObjectURL(new Blob(
      [payload.calendar.agenda],
      {type:'text/markdown;charset=utf-8'},
    )),
  };
  blobURLs=[bookURL,...Object.values(downloads)];

  bookTitle.textContent=payload.book.title;
  bookSubtitle.textContent=payload.book.subtitle;
  bookEdition.textContent=payload.book.edition;
  bookFrame.title=payload.book.title;
  bookFrame.src=bookURL+'#view=FitH';
  openBook.href=bookURL;
  downloadBook.href=bookURL;
  downloadBook.download=payload.book.filename;

  const doc=new DOMParser().parseFromString(payload.calendar.html,'text/html');
  // Only the reviewed, script-free calendar is shown; no plaintext resource fetches.
  doc.querySelectorAll('script, iframe, object, embed, base, form, link').forEach(node=>node.remove());
  for(const link of doc.querySelectorAll('a[href]')) {
    const href=link.getAttribute('href');
    if(downloads[href]){
      link.href=downloads[href];
      link.download=href;
    }else if(!href.startsWith('#')){
      link.removeAttribute('href');
      link.removeAttribute('target');
    }
  }
  const policy=doc.createElement('meta');
  policy.httpEquiv='Content-Security-Policy';
  policy.content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'";
  doc.head.prepend(policy);
  calendarFrame.removeAttribute('src');
  calendarFrame.srcdoc='<!doctype html>'+doc.documentElement.outerHTML;

  welcome.hidden=true;
  family.hidden=false;
  showView('book');
  bookTitle.focus();
}

form.addEventListener('submit',async event=>{
  event.preventDefault();
  if(button.disabled) return;
  if(!globalThis.crypto?.subtle){
    message.textContent='Please open the HTTPS website in a current browser.';
    return;
  }
  const token=++operation;
  button.disabled=true;
  button.textContent='Opening…';
  message.textContent='';
  let password=input.value;
  try {
    const response=await fetch('./family.enc.json',{cache:'no-store',credentials:'omit'});
    if(!response.ok) throw new Error('load');
    const envelope=await response.json();
    let payload;
    try {
      payload=await decrypt(envelope,password);
    }catch {
      throw new Error('unlock');
    }
    if(token!==operation) return;
    display(payload);
  } catch(error) {
    if(token!==operation) return;
    message.textContent=error.message==='unlock'
      ? 'That passcode did not unlock the family library. Check it and try again.'
      : 'The family library could not be loaded. Please reload and try again.';
    input.focus();
  } finally {
    password='';
    input.value='';
    if(token===operation){
      button.disabled=false;
      button.textContent='Open family library';
    }
  }
});

for(const viewButton of viewButtons){
  viewButton.addEventListener('click',()=>showView(viewButton.dataset.view,{focus:true}));
}

lockButton.addEventListener('click',lock);
calendarFrame.addEventListener('load',()=>{
  // srcdoc inherits the outer page's base URL; handle fragment links locally.
  if(!calendarFrame.hasAttribute('srcdoc')) return;
  const doc=calendarFrame.contentDocument;
  doc?.addEventListener('click',event=>{
    const link=event.target.closest?.('a[href^="#"]');
    if(!link) return;
    event.preventDefault();
    const target=doc.getElementById(link.getAttribute('href').slice(1));
    if(target?.tagName==='DETAILS') target.open=true;
    target?.scrollIntoView({block:'start'});
  });
});
window.addEventListener('pagehide',lock);
