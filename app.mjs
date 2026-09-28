import {decrypt} from './crypto.mjs';

const form=document.getElementById('unlock-form');
const input=document.getElementById('passphrase');
const button=document.getElementById('unlock');
const message=document.getElementById('message');
const welcome=document.getElementById('welcome');
const family=document.getElementById('family');
const calendarFrame=document.getElementById('calendar');
const calendarView=document.getElementById('calendar-view');
const practiceView=document.getElementById('practice-view');
const practiceTitle=document.getElementById('practice-title');
const practiceIntro=document.getElementById('practice-intro');
const practiceUpdated=document.getElementById('practice-updated');
const practiceCount=document.getElementById('practice-count');
const practiceList=document.getElementById('practice-list');
const unassignedMaterials=document.getElementById('unassigned-materials');
const unassignedMaterialsList=document.getElementById('unassigned-materials-list');
const viewButtons=[...document.querySelectorAll('[data-view]')];
const lockButton=document.getElementById('lock');
let blobURLs=[];
let operation=0;

function safeHttpsURL(value) {
  try {
    const url=new URL(value);
    return url.protocol==='https:' ? url.href : null;
  } catch {
    return null;
  }
}

function videoLink(video) {
  const href=safeHttpsURL(video?.url);
  if(!href || typeof video.label!=='string') return null;
  const link=document.createElement('a');
  link.className='video-link';
  link.href=href;
  link.target='_blank';
  link.rel='noreferrer noopener';
  link.textContent=video.label;
  return link;
}

function practiceCell(label) {
  const cell=document.createElement('td');
  cell.dataset.label=label;
  return cell;
}

function appendVideos(cell,videos,emptyText) {
  const links=(Array.isArray(videos) ? videos : []).map(videoLink).filter(Boolean);
  if(!links.length){
    const empty=document.createElement('span');
    empty.className='empty-value';
    empty.textContent=emptyText;
    cell.append(empty);
    return;
  }
  const list=document.createElement('ul');
  list.className='video-list';
  for(const link of links){
    const item=document.createElement('li');
    item.append(link);
    list.append(item);
  }
  cell.append(list);
}

function privateFileLink(sheet) {
  try {
    const binary=atob(sheet.base64);
    const content=Uint8Array.from(binary,char=>char.charCodeAt(0));
    const href=URL.createObjectURL(new Blob([content],{type:sheet.mime}));
    blobURLs.push(href);
    const link=document.createElement('a');
    link.className='video-link';
    link.href=href;
    link.target='_blank';
    link.rel='noreferrer noopener';
    link.textContent=sheet.label;
    link.title=`Open ${sheet.filename}`;
    return link;
  } catch {
    return null;
  }
}

function appendMusicSheet(cell,sheet) {
  if(!sheet){
    const empty=document.createElement('span');
    empty.className='empty-value';
    empty.textContent='Not added yet';
    cell.append(empty);
    return;
  }
  const link=privateFileLink(sheet);
  if(link){
    cell.append(link);
  }else{
    const invalid=document.createElement('span');
    invalid.className='empty-value';
    invalid.textContent='Sheet unavailable';
    cell.append(invalid);
  }
}

function renderUnassignedMaterials(materials) {
  const sources=Array.isArray(materials) ? materials : [];
  unassignedMaterialsList.replaceChildren();
  unassignedMaterials.hidden=!sources.length;
  for(const sheet of sources){
    const item=document.createElement('li');
    const link=privateFileLink(sheet);
    if(link){
      item.append(link);
    }else{
      item.textContent='Material unavailable';
      item.className='empty-value';
    }
    unassignedMaterialsList.append(item);
  }
}

function appendWhyImportant(cell,whyImportant) {
  const copy=document.createElement('p');
  copy.className='why-copy';
  copy.textContent=whyImportant?.text ?? 'Why this matters has not been added yet.';
  cell.append(copy);
  if(whyImportant?.video) appendVideos(cell,[whyImportant.video],'');
}

function renderPractice(practice) {
  const source=practice ?? {
    title:'Lincoln’s Violin Practice',
    intro:'Practice items have not been added yet.',
    updated:'',
    unassigned_materials:[],
    items:[],
  };
  const items=Array.isArray(source.items) ? source.items : [];
  practiceTitle.textContent=source.title;
  practiceIntro.textContent=source.intro;
  practiceUpdated.textContent=source.updated ? `Updated ${source.updated}` : '';
  practiceCount.textContent=`${items.length} focus ${items.length===1 ? 'area' : 'areas'}`;
  practiceList.replaceChildren();

  for(const item of items){
    const row=document.createElement('tr');

    const technique=practiceCell('Technique');
    const heading=document.createElement('strong');
    heading.textContent=item.technique;
    technique.append(heading);

    const description=practiceCell('Short description');
    description.textContent=item.description;

    const focus=practiceCell('Coach focus / effort');
    focus.textContent=item.coach_focus;

    const musicSheet=practiceCell('Sheet music');
    appendMusicSheet(musicSheet,item.music_sheet);

    const references=practiceCell('Reference videos from others');
    appendVideos(references,item.reference_videos,'Reference link to add');

    const teacherReference=practiceCell('Reference from Teacher Tony');
    appendVideos(
      teacherReference,
      item.teacher_tony_reference ? [item.teacher_tony_reference] : [],
      'Not added yet',
    );

    const ownVideo=practiceCell('Lincoln’s video');
    appendVideos(ownVideo,item.practice_video ? [item.practice_video] : [],'Not added yet');

    const whyImportant=practiceCell('Why important');
    appendWhyImportant(whyImportant,item.why_important);

    row.append(technique,description,focus,musicSheet,references,teacherReference,ownVideo,whyImportant);
    practiceList.append(row);
  }
  renderUnassignedMaterials(source.unassigned_materials);
}

function clearPractice() {
  practiceTitle.textContent='';
  practiceIntro.textContent='';
  practiceUpdated.textContent='';
  practiceCount.textContent='';
  practiceList.replaceChildren();
  unassignedMaterialsList.replaceChildren();
  unassignedMaterials.hidden=true;
}

function showView(name,{focus=false}={}) {
  const practiceSelected=name==='practice';
  practiceView.hidden=!practiceSelected;
  calendarView.hidden=practiceSelected;
  for(const viewButton of viewButtons){
    const selected=viewButton.dataset.view===name;
    viewButton.setAttribute('aria-pressed',String(selected));
  }
  if(focus) (practiceSelected ? practiceTitle : calendarFrame).focus();
}

function lock() {
  operation++;
  calendarFrame.removeAttribute('srcdoc');
  calendarFrame.src='about:blank';
  clearPractice();
  for(const url of blobURLs) URL.revokeObjectURL(url);
  blobURLs=[];
  input.value='';
  message.textContent='';
  family.hidden=true;
  welcome.hidden=false;
  showView('calendar');
  button.disabled=false;
  button.textContent='Open family library';
  input.focus();
}

function display(payload) {
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
  blobURLs=Object.values(downloads);

  const doc=new DOMParser().parseFromString(payload.calendar.html,'text/html');
  // Only the reviewed, script-free calendar is shown; no plaintext resource fetches.
  doc.querySelectorAll('script, iframe, object, embed, base, form, link').forEach(node=>node.remove());
  for(const link of doc.querySelectorAll('a[href]')) {
    const href=link.getAttribute('href');
    if(downloads[href]){
      link.href=downloads[href];
      link.download=href;
    }else if(/^https:\/\//i.test(href)){
      link.target='_blank';
      link.rel='noreferrer noopener';
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

  renderPractice(payload.violinPractice);
  welcome.hidden=true;
  family.hidden=false;
  showView('calendar');
  calendarFrame.focus();
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
