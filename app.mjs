import {decrypt,encryptPracticeData,decryptPracticeData} from './crypto.mjs';

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
const currentProgram=document.getElementById('current-program');
const currentProgramTitle=document.getElementById('current-program-title');
const currentProgramIntro=document.getElementById('current-program-intro');
const currentProgramPieces=document.getElementById('current-program-pieces');
const practiceRunner=document.getElementById('practice-runner');
const practiceCards=document.getElementById('practice-cards');
const assignmentEditor=document.getElementById('assignment-editor');
const assignmentOptions=document.getElementById('assignment-options');
const customTaskForm=document.getElementById('custom-task-form');
const customTaskList=document.getElementById('custom-task-list');
const assignmentMessage=document.getElementById('assignment-message');
const todayDashboard=document.getElementById('today-dashboard');
const todayDate=document.getElementById('today-date');
const todayTaskList=document.getElementById('today-task-list');
const todayEmpty=document.getElementById('today-empty');
const todayTimer=document.getElementById('today-timer');
const startPracticeButton=document.getElementById('start-practice');
const pausePracticeButton=document.getElementById('pause-practice');
const dailySummary=document.getElementById('daily-summary');
const summaryTotal=document.getElementById('summary-total');
const summaryLog=document.getElementById('summary-log');
const summaryRecordings=document.getElementById('summary-recordings');
const practiceViewButtons=[...document.querySelectorAll('.practice-tools button')];
const practiceSession=document.getElementById('practice-session');
const sessionMode=document.getElementById('session-mode');
const sessionFocus=document.getElementById('session-focus');
const sessionProgress=document.getElementById('session-progress');
const youtubePlayerHost=document.getElementById('youtube-player');
const recordingArea=document.getElementById('recording-area');
const cameraPreview=document.getElementById('camera-preview');
const recordingStatus=document.getElementById('recording-status');
const stopRecordingButton=document.getElementById('stop-recording');
const savedTakes=document.getElementById('saved-takes');
const unassignedMaterials=document.getElementById('unassigned-materials');
const unassignedMaterialsList=document.getElementById('unassigned-materials-list');
const viewButtons=[...document.querySelectorAll('[data-view]')];
const lockButton=document.getElementById('lock');
let blobURLs=[];
let operation=0;
let ytPlayer=null;
let ytApiPromise=null;
let activeSession=null;
let cameraStream=null;
let recorder=null;
let recordingTimer=0;
let googleIdentityPromise=null;
let googleTokenClient=null;
let uploadAccessToken='';
let uploadTokenExpiresAt=0;
let archivePassword='';
let practiceItems=[];
let dailyState=null;
let timerHandle=0;
let persistedTimerMinute=0;
let customTasks=[];
let dailySaveQueue=Promise.resolve();

const PRACTICE_MODES={
  listen:{label:'Listen to Teacher',records:false},
  together:{label:'Listen & Play Together',records:true},
  solo:{label:'Play Yourself',records:true},
};
const YOUTUBE_UPLOAD_SCOPE='https://www.googleapis.com/auth/youtube';
const youtubeConfig=window.FAMILY_YOUTUBE_CONFIG ?? {};
let ownedPlaylists=null;

function loadGoogleIdentity(){
  if(window.google?.accounts?.oauth2)return Promise.resolve();
  if(googleIdentityPromise)return googleIdentityPromise;
  googleIdentityPromise=new Promise((resolve,reject)=>{
    const script=document.createElement('script');
    script.src='https://accounts.google.com/gsi/client';
    script.async=true;
    script.onload=()=>{
      if(window.google?.accounts?.oauth2)resolve();
      else {googleIdentityPromise=null;reject(new Error('Google sign-in did not initialize.'));}
    };
    script.onerror=()=>{googleIdentityPromise=null;reject(new Error('Google sign-in could not be loaded.'));};
    document.head.append(script);
  });
  return googleIdentityPromise;
}

function requestUploadAccess(){
  if(uploadAccessToken && Date.now()<uploadTokenExpiresAt)return Promise.resolve(uploadAccessToken);
  if(!youtubeConfig.clientId)throw new Error('Add your Google OAuth Web client ID to youtube-config.js before uploading.');
  if(!window.google?.accounts?.oauth2)throw new Error('Google sign-in is still loading. Wait a moment, then click Upload again.');
  return new Promise((resolve,reject)=>{
    googleTokenClient=window.google.accounts.oauth2.initTokenClient({
      client_id:youtubeConfig.clientId,
      scope:YOUTUBE_UPLOAD_SCOPE,
      hint:youtubeConfig.accountHint || '',
      prompt:'select_account',
      callback:response=>{
        if(response.error){reject(new Error(`Google sign-in failed: ${response.error}`));return;}
        if(!response.access_token){reject(new Error('Google did not return upload permission.'));return;}
        uploadAccessToken=response.access_token;
        uploadTokenExpiresAt=Date.now()+Math.max(0,Number(response.expires_in || 0)-30)*1000;
        resolve(uploadAccessToken);
      },
      error_callback:response=>reject(new Error(response.message || 'Google sign-in popup was closed or blocked.')),
    });
    googleTokenClient.requestAccessToken();
  });
}

async function youtubeRequest(url,token,options={}){
  const response=await fetch(url,{...options,headers:{Authorization:`Bearer ${token}`,...options.headers}});
  const result=await response.json().catch(()=>({}));
  if(!response.ok){
    if(response.status===401)uploadAccessToken='';
    throw new Error(result.error?.message || `YouTube request failed (${response.status}).`);
  }
  return result;
}

async function fetchOwnedPlaylists(){
  const token=await requestUploadAccess();
  const channel=await youtubeRequest('https://www.googleapis.com/youtube/v3/channels?part=contentDetails&mine=true',token);
  const uploadsPlaylist=channel.items?.[0]?.contentDetails?.relatedPlaylists?.uploads;
  const playlists=[];
  let pageToken='';
  do{
    const query=new URLSearchParams({part:'snippet',mine:'true',maxResults:'50'});
    if(pageToken)query.set('pageToken',pageToken);
    const result=await youtubeRequest(`https://www.googleapis.com/youtube/v3/playlists?${query}`,token);
    playlists.push(...(result.items ?? []));
    pageToken=result.nextPageToken || '';
  }while(pageToken);
  ownedPlaylists=playlists.filter(playlist=>playlist.id!==uploadsPlaylist).map(playlist=>({id:playlist.id,title:playlist.snippet?.title || 'Untitled playlist'}));
  return ownedPlaylists;
}

async function addVideoToPlaylist(videoId,playlistId,token){
  return youtubeRequest('https://www.googleapis.com/youtube/v3/playlistItems?part=snippet',token,{
    method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({snippet:{playlistId,resourceId:{kind:'youtube#video',videoId}}}),
  });
}

async function uploadVideo(blob,session,title,description,playlistId=''){
  const token=await requestUploadAccess();
  const titleText=title.trim();
  if(!titleText)throw new Error('Enter a title for this practice video.');
  const boundary=`lincoln-family-${crypto.randomUUID()}`;
  const metadata={
    snippet:{
      title:titleText.slice(0,100),
      description:description.trim().slice(0,5000),
      categoryId:'10',
    },
    status:{privacyStatus:'private'},
  };
  const type=blob.type || 'video/webm';
  const body=new Blob([
    `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify(metadata)}\r\n`,
    `--${boundary}\r\nContent-Type: ${type}\r\n\r\n`,
    blob,
    `\r\n--${boundary}--`,
  ],{type:`multipart/related; boundary=${boundary}`});
  const response=await fetch('https://www.googleapis.com/upload/youtube/v3/videos?uploadType=multipart&part=snippet,status',{
    method:'POST',
    headers:{Authorization:`Bearer ${token}`,'Content-Type':`multipart/related; boundary=${boundary}`},
    body,
  });
  const result=await response.json().catch(()=>({}));
  if(!response.ok){
    const detail=result.error?.message || `YouTube upload failed (${response.status}).`;
    if(response.status===401)uploadAccessToken='';
    throw new Error(detail);
  }
  if(!result.id)throw new Error('YouTube received the upload but did not return a video ID. Check YouTube Studio.');
  let playlistError='';
  if(playlistId){
    try{await addVideoToPlaylist(result.id,playlistId,token);}
    catch(error){playlistError=error.message || 'The video uploaded, but YouTube could not add it to the playlist.';}
  }
  return {id:result.id,playlistError};
}

function youtubeId(value) {
  try {
    const url=new URL(value);
    if(!['youtube.com','www.youtube.com','m.youtube.com','youtu.be','www.youtu.be','www.youtube-nocookie.com'].includes(url.hostname))return null;
    let id='';
    if(url.hostname.endsWith('youtu.be')) id=url.pathname.split('/').filter(Boolean)[0] ?? '';
    else if(url.pathname==='/watch') id=url.searchParams.get('v') ?? '';
    else id=url.pathname.split('/').filter(Boolean).at(-1) ?? '';
    return /^[A-Za-z0-9_-]{11}$/.test(id) ? id : null;
  } catch {return null;}
}

function loadYouTubeAPI() {
  if(window.YT?.Player)return Promise.resolve(window.YT);
  if(ytApiPromise)return ytApiPromise;
  ytApiPromise=new Promise((resolve,reject)=>{
    const previous=window.onYouTubeIframeAPIReady;
    window.onYouTubeIframeAPIReady=()=>{
      previous?.();
      resolve(window.YT);
    };
    const script=document.createElement('script');
    script.src='https://www.youtube.com/iframe_api';
    script.async=true;
    script.onerror=()=>reject(new Error('YouTube player could not be loaded.'));
    document.head.append(script);
  });
  return ytApiPromise;
}

function getPlayer(videoId) {
  return loadYouTubeAPI().then(YT=>new Promise((resolve,reject)=>{
    if(ytPlayer){
      ytPlayer.cueVideoById({videoId,startSeconds:0});
      resolve(ytPlayer);
      return;
    }
    youtubePlayerHost.replaceChildren();
    const mount=document.createElement('div');
    mount.id='youtube-player-frame';
    youtubePlayerHost.append(mount);
    ytPlayer=new YT.Player(mount.id,{
      width:'100%',height:'390',videoId,
      playerVars:{playsinline:1,origin:location.origin,rel:0},
      events:{
        onReady:()=>resolve(ytPlayer),
        onStateChange:event=>handlePlayerState(event.data),
        onError:()=>{
          recordingStatus.textContent='This reference video cannot be embedded. Open it on YouTube or choose another reference.';
          cancelSession();
        },
      },
    });
    setTimeout(()=>{if(!ytPlayer?.getPlayerState)reject(new Error('YouTube player did not become ready.'));},15000);
  }));
}

function valuesFor(card) {
  const start=parseVideoTime(card.querySelector('[data-time="start"]').value || '0:00');
  const endText=card.querySelector('[data-time="end"]').value.trim();
  const end=endText ? parseVideoTime(endText) : null;
  const repeats=Number(card.querySelector('[data-time="repeats"]').value);
  const select=card.querySelector('[data-reference]');
  const video=JSON.parse(select.selectedOptions[0]?.dataset.video || 'null');
  if(!video || !youtubeId(video.url))throw new Error('Choose a YouTube reference video for this focus.');
  if(!Number.isFinite(start) || (end!==null && (!Number.isFinite(end) || end<=start)))throw new Error('Enter a valid end time later than the start time, or leave it blank to use the full video.');
  if(!Number.isInteger(repeats) || repeats<1 || repeats>20)throw new Error('Choose between 1 and 20 repetitions.');
  return {start,end,repeats,video,videoId:youtubeId(video.url),duration:end===null?null:end-start};
}

function parseVideoTime(value){
  const text=value.trim();
  if(!text)return Number.NaN;
  if(/^\d+:\d{1,2}(?:\.\d+)?$/.test(text)){
    const [minutes,seconds]=text.split(':').map(Number);
    return seconds<60 ? minutes*60+seconds : Number.NaN;
  }
  return Number(text);
}

function formatVideoTime(seconds){
  const whole=Math.max(0,Math.floor(seconds));
  return `${Math.floor(whole/60)}:${String(whole%60).padStart(2,'0')}`;
}

function formatClipRange(start,end){
  return end===null && start===0 ? 'Whole video' : `${formatVideoTime(start)}–${end===null?'end of video':formatVideoTime(end)}`;
}

function playAssignedClip(player,settings){
  const options={videoId:settings.videoId,startSeconds:settings.start};
  if(settings.end!==null)options.endSeconds=settings.end;
  player.loadVideoById(options);
  player.playVideo();
}

async function getFullClipDuration(settings){
  const player=await getPlayer(settings.videoId);
  player.cueVideoById({videoId:settings.videoId,startSeconds:settings.start});
  const deadline=Date.now()+15000;
  while(Date.now()<deadline){
    const duration=Number(player.getDuration?.() || 0);
    if(duration>settings.start)return duration-settings.start;
    await new Promise(resolve=>setTimeout(resolve,150));
  }
  throw new Error('Could not read this video’s duration. Set an end time manually; live videos need an end time.');
}

function localDateKey(date=new Date()){
  return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
}

function newDailyState(date=localDateKey()){
  return {date,tasks:[],elapsedMs:0,runningSince:0,completedIds:[],practiceLog:[]};
}

function dailyStorageKey(date){return `lincoln-family-practice-${date}`;}

async function loadDailyState(){
  const date=localDateKey();
  const password=archivePassword;
  dailyState=newDailyState(date);
  try{
    const raw=localStorage.getItem(dailyStorageKey(date));
    if(raw){
      const restored=await decryptPracticeData(JSON.parse(raw),password);
      if(password!==archivePassword)return;
      if(restored?.date===date && Array.isArray(restored.tasks))dailyState={...newDailyState(date),...restored,runningSince:0};
    }
  }catch{if(password!==archivePassword)return;dailyState=newDailyState(date);}
  customTasks=dailyState.tasks.filter(task=>task.custom);
  persistedTimerMinute=Math.floor(dailyState.elapsedMs/60000);
  renderAssignmentEditor();
  renderToday();
  renderSummary();
  renderPracticeCards(assignedItems());
}

function saveDailyState(){
  if(!dailyState || !archivePassword)return Promise.resolve();
  const password=archivePassword;
  const snapshot=JSON.parse(JSON.stringify({...dailyState,runningSince:0,elapsedMs:elapsedToday()}));
  dailySaveQueue=dailySaveQueue.catch(()=>{}).then(async()=>{
    if(!password)return;
    const encrypted=await encryptPracticeData(snapshot,password);
    localStorage.setItem(dailyStorageKey(snapshot.date),JSON.stringify(encrypted));
  }).catch(()=>{});
  return dailySaveQueue;
}

function elapsedToday(){
  return (dailyState?.elapsedMs ?? 0)+(dailyState?.runningSince ? Date.now()-dailyState.runningSince : 0);
}

function formatDuration(milliseconds){
  const seconds=Math.max(0,Math.floor(milliseconds/1000));
  const minutes=Math.floor(seconds/60);
  const remainder=seconds%60;
  return minutes ? `${minutes} min${remainder?` ${remainder} sec`:''}` : `${remainder} sec`;
}

function updateTimerDisplay(){
  const elapsed=elapsedToday();
  todayTimer.textContent=formatDuration(elapsed);
  const running=Boolean(dailyState?.runningSince);
  startPracticeButton.textContent=running?'Practice in progress':'Start';
  startPracticeButton.disabled=running || !dailyState?.tasks.length;
  pausePracticeButton.disabled=!running;
  const minute=Math.floor(elapsed/60000);
  if(running && minute>persistedTimerMinute){persistedTimerMinute=minute;saveDailyState();}
}

function startDailyTimer(){
  if(!dailyState || dailyState.runningSince)return;
  dailyState.runningSince=Date.now();
  if(recorder?.state==='paused')recorder.resume();
  if(activeSession && activeSession.mode!=='solo')ytPlayer?.playVideo();
  if(activeSession?.mode==='solo' && activeSession.soloRecording)scheduleSoloAttemptEnd(activeSession);
  if(!timerHandle)timerHandle=window.setInterval(updateTimerDisplay,1000);
  updateTimerDisplay();
  saveDailyState();
}

function pauseDailyTimer(){
  if(!dailyState?.runningSince)return;
  dailyState.elapsedMs=elapsedToday();
  dailyState.runningSince=0;
  persistedTimerMinute=Math.floor(dailyState.elapsedMs/60000);
  if(recorder?.state==='recording')recorder.pause();
  if(activeSession && activeSession.mode!=='solo')ytPlayer?.pauseVideo();
  if(activeSession?.mode==='solo' && activeSession.soloRecording){
    window.clearTimeout(recordingTimer);
    activeSession.soloTimeLeft=Math.max(0,activeSession.soloTimeLeft-(Date.now()-activeSession.soloTimerStartedAt));
  }
  updateTimerDisplay();
  saveDailyState();
}

function field(labelText,value,placeholder,type='text'){
  const label=document.createElement('label');
  label.textContent=labelText;
  const input=document.createElement('input');
  input.type=type;
  input.value=value ?? '';
  input.placeholder=placeholder;
  if(type==='number'){input.min='1';input.max='20';input.step='1';}
  label.append(input);
  return {label,input};
}

function tableVideosFor(item){
  const whyVideo=item.why_important?.video;
  const videos=[
    item.teacher_tony_reference,
    ...(item.reference_videos ?? []),
    ...(whyVideo ? [{...whyVideo,label:whyVideo.label || 'Why this matters'}] : []),
  ];
  const seen=new Set();
  return videos.filter(video=>{
    const url=video?.url;
    if(!youtubeId(url) || seen.has(url))return false;
    seen.add(url);
    return true;
  });
}

function renderAssignmentEditor(){
  assignmentOptions.replaceChildren();
  for(const item of practiceItems){
    const refs=tableVideosFor(item);
    if(!refs.length)continue;
    const saved=dailyState?.tasks.find(task=>task.sourceId===item.id);
    const row=document.createElement('div');row.className='assignment-option';
    const check=document.createElement('input');check.type='checkbox';check.checked=Boolean(saved);check.setAttribute('aria-label',`Assign ${item.technique}`);
    const body=document.createElement('div');body.className='assignment-option-body';
    const heading=document.createElement('strong');heading.textContent=item.technique;
    const fields=document.createElement('div');fields.className='assignment-option-fields';
    const sourceLabel=document.createElement('label');sourceLabel.textContent='Reference video';
    const select=document.createElement('select');
    for(const video of refs){const option=document.createElement('option');option.value=video.url;option.textContent=video.label;option.dataset.label=video.label;select.append(option);}
    if(saved)select.value=saved.url;
    sourceLabel.append(select);
    const start=field('Start','', '0:00');
    const end=field('End','', 'Full video');
    const repeats=field('Repetitions','3','1–20','number');
    if(saved){start.input.value=saved.start===0?'':formatVideoTime(saved.start);end.input.value=saved.end===null?'':formatVideoTime(saved.end);repeats.input.value=saved.repeats;}
    fields.append(sourceLabel,start.label,end.label,repeats.label);
    body.append(heading,fields);
    row.append(check,body);
    row.dataset.assignmentId=item.id;
    assignmentOptions.append(row);
  }
  renderCustomTaskList();
}

function renderCustomTaskList(){
  customTaskList.replaceChildren();
  for(const task of customTasks){
    const row=document.createElement('div');row.className='custom-task-row';
    const copy=document.createElement('span');copy.textContent=`${task.title} · ${formatClipRange(task.start,task.end)} · ${task.repeats} repetitions`;
    const remove=document.createElement('button');remove.type='button';remove.textContent='Remove';
    remove.addEventListener('click',()=>{customTasks=customTasks.filter(entry=>entry.id!==task.id);renderCustomTaskList();});
    row.append(copy,remove);customTaskList.append(row);
  }
}

function saveAssignment(){
  const tasks=[];
  for(const row of assignmentOptions.querySelectorAll('.assignment-option')){
    const check=row.querySelector('input[type="checkbox"]');
    if(!check.checked)continue;
    const item=practiceItems.find(entry=>entry.id===row.dataset.assignmentId);
    const select=row.querySelector('select');
    const inputs=row.querySelectorAll('.assignment-option-fields input');
    const start=parseVideoTime(inputs[0].value || '0:00');
    const endText=inputs[1].value.trim();
    const end=endText ? parseVideoTime(endText) : null;
    const repeats=Number(inputs[2].value);
    const video=tableVideosFor(item ?? {}).find(entry=>entry?.url===select.value);
    if(!video || !Number.isFinite(start) || (end!==null && (!Number.isFinite(end) || end<=start)) || !Number.isInteger(repeats) || repeats<1 || repeats>20){
      assignmentMessage.textContent=`Check the link, timestamps, and repetition count for ${item?.technique ?? 'the selected task'}.`;
      return;
    }
    tasks.push({id:item.id,sourceId:item.id,title:item.technique,description:item.description,coachFocus:item.coach_focus,url:video.url,label:video.label,start,end,repeats,custom:false});
  }
  tasks.push(...customTasks);
  if(!tasks.length){assignmentMessage.textContent='Select a focus or add a YouTube task first.';return;}
  dailyState.tasks=tasks;
  dailyState.completedIds=dailyState.completedIds.filter(id=>tasks.some(task=>task.id===id));
  assignmentMessage.textContent='Today’s assignment is saved.';
  renderToday();renderSummary();renderPracticeCards(assignedItems());
  saveDailyState();
  showPracticePane('today');
}

function assignedItems(){
  return (dailyState?.tasks ?? []).map(task=>({
    id:task.id,technique:task.title,description:task.description || '',coach_focus:task.coachFocus || '',
    teacher_tony_reference:{label:task.label,url:task.url},reference_videos:[],
    assignment:task,
  }));
}

function renderToday(){
  if(!dailyState)return;
  todayDate.textContent=new Date(`${dailyState.date}T12:00:00`).toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric'});
  todayTaskList.replaceChildren();
  todayEmpty.hidden=dailyState.tasks.length>0;
  for(const task of dailyState.tasks){
    const row=document.createElement('article');row.className='today-task';
    const copy=document.createElement('div');
    const title=document.createElement('strong');title.textContent=task.title;
    const details=document.createElement('p');details.textContent=`${formatClipRange(task.start,task.end)} · ${task.repeats} repetitions · ${task.label}`;
    const state=document.createElement('p');state.className='task-complete';state.textContent=dailyState.completedIds.includes(task.id)?'Completed':'Not completed yet';
    copy.append(title,details,state);
    const actions=document.createElement('div');
    const open=document.createElement('button');open.type='button';open.textContent='Practice';
    open.addEventListener('click',()=>{
      startDailyTimer();practiceRunner.hidden=false;practiceRunner.scrollIntoView({behavior:'smooth',block:'start'});
      const card=[...practiceCards.children].find(node=>node.dataset.taskId===task.id);
      card?.scrollIntoView({behavior:'smooth',block:'center'});
    });
    const done=document.createElement('button');done.type='button';done.textContent=dailyState.completedIds.includes(task.id)?'Undo done':'Mark done';
    done.addEventListener('click',()=>{
      if(dailyState.completedIds.includes(task.id))dailyState.completedIds=dailyState.completedIds.filter(id=>id!==task.id);
      else dailyState.completedIds.push(task.id);
      renderToday();renderSummary();saveDailyState();
    });
    actions.append(open,done);row.append(copy,actions);todayTaskList.append(row);
  }
  updateTimerDisplay();
}

function renderSummary(){
  if(!dailyState)return;
  summaryTotal.textContent=`Total practice time: ${formatDuration(elapsedToday())}`;
  summaryLog.replaceChildren();
  const heading=document.createElement('p');
  heading.textContent=`${dailyState.completedIds.length} of ${dailyState.tasks.length} tasks completed`;
  summaryLog.append(heading);
  for(const task of dailyState.tasks){
    const row=document.createElement('article');row.className='summary-entry';
    const copy=document.createElement('div');const title=document.createElement('strong');title.textContent=task.title;
    const detail=document.createElement('p');detail.textContent=`${formatClipRange(task.start,task.end)} · ${task.repeats} assigned repetitions · ${dailyState.completedIds.includes(task.id)?'Completed':'Not completed'}`;
    const reference=videoLink({label:'Open reference video',url:task.url});
    copy.append(title,detail);if(reference)copy.append(reference);row.append(copy);summaryLog.append(row);
  }
  for(const entry of dailyState.practiceLog){
    const row=document.createElement('article');row.className='summary-entry';
    const copy=document.createElement('div');
    const text=document.createElement('p');
    text.textContent=`${entry.taskTitle}: ${entry.event || entry.mode} · ${new Date(entry.at).toLocaleTimeString([], {hour:'numeric',minute:'2-digit'})}`;
    copy.append(text);
    if(entry.videoId){const link=document.createElement('a');link.href=`https://youtu.be/${encodeURIComponent(entry.videoId)}`;link.target='_blank';link.rel='noreferrer noopener';link.textContent='Open uploaded video';copy.append(link);}
    row.append(copy);summaryLog.append(row);
  }
}

function markTaskComplete(taskId){
  if(!dailyState || dailyState.completedIds.includes(taskId))return;
  dailyState.completedIds.push(taskId);
  renderToday();renderSummary();
  for(const card of practiceCards.children){
    if(card.dataset.taskId!==taskId)continue;
    const button=card.querySelector('.task-done-button');
    if(button)button.textContent='Completed ✓';
  }
  saveDailyState();
}

function showPracticePane(name){
  assignmentEditor.hidden=name!=='assignment';
  todayDashboard.hidden=name!=='today';
  dailySummary.hidden=name!=='summary';
  if(name!=='today' && !activeSession)practiceRunner.hidden=true;
  for(const button of practiceViewButtons)button.setAttribute('aria-pressed',String(button.id===`show-${name}`));
  if(name==='summary'){
    pauseDailyTimer();
    if(recorder && ['recording','paused'].includes(recorder.state))recorder.stop();
    if(ytPlayer?.pauseVideo)ytPlayer.pauseVideo();
    activeSession=null;
    releaseCamera();
    practiceRunner.hidden=true;
    renderSummary();
    summaryRecordings.append(savedTakes);
  }else if(name==='today'){
    practiceSession.append(savedTakes);
  }
}

function renderPracticeCards(items) {
  practiceCards.replaceChildren();
  const playable=items.filter(item=>tableVideosFor(item).length>0);
  practiceRunner.hidden=!playable.length;
  for(const item of playable){
    const card=document.createElement('article');
    card.className='practice-card';
    const title=document.createElement('h3');
    title.textContent=item.technique;
    const description=document.createElement('p');
    description.className='card-description';
    description.textContent=item.description;
    const coach=document.createElement('p');
    coach.className='card-coach-focus';
    coach.textContent=item.coach_focus;
    const sourceLabel=document.createElement('label');
    sourceLabel.textContent='Reference video';
    const select=document.createElement('select');
    select.dataset.reference='';
    const references=tableVideosFor(item);
    for(const video of references){
      const option=document.createElement('option');
      option.value=video.url;
      option.dataset.video=JSON.stringify(video);
      option.textContent=video.label;
      select.append(option);
    }
    if(item.assignment)select.value=item.assignment.url;
    sourceLabel.append(select);
    const settings=document.createElement('div');
    settings.className='clip-settings';
    for(const [key,labelText,placeholder,min] of [
      ['start','Start time','0:00','0'],
      ['end','End time','e.g. 0:18','0.1'],
      ['repeats','Repeat count','Enter count','1'],
    ]){
      const label=document.createElement('label');
      label.textContent=labelText;
      const input=document.createElement('input');
      input.type=key==='repeats'?'number':'text';
      if(key==='repeats')input.min=min;
      if(key!=='repeats')input.inputMode='decimal';
      input.step=key==='repeats'?'1':'any';
      input.placeholder=placeholder;
      input.value=item.assignment
        ? key==='start' ? formatVideoTime(item.assignment.start)
          : key==='end' ? item.assignment.end===null?'':formatVideoTime(item.assignment.end)
            : String(item.assignment.repeats)
        : '';
      input.dataset.time=key;
      label.append(input);
      settings.append(label);
    }
    const hint=document.createElement('p');
    hint.className='settings-hint';
    hint.textContent='Enter timestamps as mm:ss and the repetition count from the teacher’s assignment.';
    const modes=document.createElement('div');
    modes.className='mode-buttons';
    for(const [mode,config] of Object.entries(PRACTICE_MODES)){
      const button=document.createElement('button');
      button.type='button';
      button.className=`mode-button${mode==='listen'?' mode-listen':''}`;
      button.dataset.practiceMode=mode;
      button.textContent=config.label;
      button.addEventListener('click',()=>beginSession(card,item,mode));
      modes.append(button);
    }
    const done=document.createElement('button');
    done.type='button';done.className='task-done-button';
    const isDone=dailyState?.completedIds.includes(item.id);
    done.textContent=isDone?'Completed ✓':'Mark this task done';
    done.addEventListener('click',()=>{
      if(!dailyState)return;
      if(dailyState.completedIds.includes(item.id))dailyState.completedIds=dailyState.completedIds.filter(id=>id!==item.id);
      else dailyState.completedIds.push(item.id);
      done.textContent=dailyState.completedIds.includes(item.id)?'Completed ✓':'Mark this task done';
      renderToday();renderSummary();saveDailyState();
    });
    card.dataset.taskId=item.id;
    card.append(title,description,coach,sourceLabel,settings,hint,modes,done);
    practiceCards.append(card);
  }
}

async function beginSession(card,item,mode){
  cancelSession();
  let settings;
  try{settings=valuesFor(card);}catch(error){
    practiceSession.hidden=false;
    sessionMode.textContent='Setup needed';
    sessionFocus.textContent=item.technique;
    recordingStatus.textContent=error.message;
    recordingArea.hidden=false;
    youtubePlayerHost.hidden=true;
    return;
  }
  startDailyTimer();
  dailyState?.practiceLog.push({taskId:item.id,taskTitle:item.technique,mode:PRACTICE_MODES[mode].label,at:new Date().toISOString()});
  renderSummary();
  saveDailyState();
  activeSession={...settings,item,mode,remaining:settings.repeats,soloAttempts:0};
  practiceSession.hidden=false;
  recordingArea.hidden=false;
  cameraPreview.hidden=true;
  stopRecordingButton.hidden=true;
  youtubePlayerHost.hidden=mode==='solo';
  sessionMode.textContent=PRACTICE_MODES[mode].label;
  sessionFocus.textContent=item.technique;
  sessionProgress.textContent=mode==='solo'
    ? `Take 0 of ${settings.repeats}`
    : `${settings.repeats} plays left`;
  recordingStatus.textContent='';
  try{
    if(mode!=='solo'){
      const player=await getPlayer(settings.videoId);
      if(!activeSession)return;
      if(mode==='together')await startCamera(true);
      if(!activeSession)return;
      playAssignedClip(player,settings);
    }
    if(mode==='solo'){
      if(settings.end===null){
        youtubePlayerHost.hidden=false;
        settings.duration=await getFullClipDuration(settings);
        activeSession.duration=settings.duration;
        youtubePlayerHost.hidden=true;
      }
      await startCamera(false);
    }
    else recordingStatus.textContent='Listen to the assigned clip.';
  }catch(error){
    recordingStatus.textContent=error.message || 'Could not start this practice mode.';
  }
}

function chooseMimeType(){
  const choices=['video/mp4;codecs=avc1.42E01E,mp4a.40.2','video/mp4','video/webm;codecs=vp8,opus','video/webm'];
  return choices.find(type=>MediaRecorder.isTypeSupported(type)) || '';
}

async function startCamera(continuous){
  if(!activeSession)return;
  const session=activeSession;
  if(!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder){
    recordingStatus.textContent='Camera recording is not supported in this browser. Open this family page over HTTPS in a current browser.';
    return;
  }
  try{
    const stream=await navigator.mediaDevices.getUserMedia({video:true,audio:true});
    if(activeSession!==session){stream.getTracks().forEach(track=>track.stop());return;}
    cameraStream=stream;
    cameraPreview.srcObject=cameraStream;
    cameraPreview.hidden=false;
    stopRecordingButton.hidden=false;
    recordingArea.hidden=false;
    if(continuous){
      beginRecording();
      recordingStatus.textContent='Recording Lincoln while the reference plays.';
    }else{
      recordSoloAttempt();
    }
  }catch{
    recordingStatus.textContent='Camera or microphone access was not allowed. Allow access and try again.';
  }
}

function beginRecording(){
  if(!cameraStream || !activeSession)return;
  const mimeType=chooseMimeType();
  const chunks=[];
  const session=activeSession;
  recorder=new MediaRecorder(cameraStream,mimeType?{mimeType}:undefined);
  const currentRecorder=recorder;
  currentRecorder.ondataavailable=event=>{if(event.data.size)chunks.push(event.data);};
  currentRecorder.onstop=()=>saveRecording(session,chunks,currentRecorder.mimeType || mimeType || 'video/webm');
  recorder.start();
}

function scheduleSoloAttemptEnd(session){
  window.clearTimeout(recordingTimer);
  session.soloTimerStartedAt=Date.now();
  recordingTimer=window.setTimeout(()=>finishSoloAttempt(session),Math.max(0,session.soloTimeLeft));
}

function finishSoloAttempt(session,naturalCompletion=true){
  if(activeSession!==session)return;
  session.soloRecording=false;
  session.soloTimeLeft=0;
  if(recorder && ['recording','paused'].includes(recorder.state))recorder.stop();
  session.soloAttempts++;
  sessionProgress.textContent=`Take ${session.soloAttempts} of ${session.repeats}`;
  if(session.soloAttempts<session.repeats){
    recordingStatus.textContent=`Take saved. ${session.repeats-session.soloAttempts} more to go.`;
    const next=document.createElement('button');
    next.type='button';next.className='next-take';
    next.textContent=`Record next take (${session.repeats-session.soloAttempts} left)`;
    next.addEventListener('click',()=>{next.remove();recordSoloAttempt();},{once:true});
    recordingStatus.append(' ',next);
  }else{
    recordingStatus.textContent=naturalCompletion
      ? 'Practice complete. Review or upload the takes in Summary.'
      : 'Recording stopped. Review or upload the take in Summary.';
    releaseCamera();
    if(naturalCompletion)markTaskComplete(session.item.id);
    activeSession=null;
  }
}

function recordSoloAttempt(){
  if(!activeSession || !cameraStream)return;
  beginRecording();
  const session=activeSession;
  const attempt=session.soloAttempts+1;
  session.soloRecording=true;
  session.soloTimeLeft=session.duration*1000;
  recordingStatus.textContent=`Play Yourself — take ${attempt} of ${session.repeats}. Recording for ${Math.ceil(session.duration)} seconds.`;
  sessionProgress.textContent=`Take ${attempt} of ${session.repeats}`;
  scheduleSoloAttemptEnd(session);
}

function saveRecording(session,chunks,type){
  if(!chunks.length)return;
  const blob=new Blob(chunks,{type});
  const url=URL.createObjectURL(blob);
  blobURLs.push(url);
  const take=document.createElement('div');
  take.className='saved-take';
  const video=document.createElement('video');
  video.controls=true;
  video.playsInline=true;
  video.src=url;
  const link=document.createElement('a');
  link.href=url;
  link.download=`lincoln-${session?.item.id ?? 'violin'}-${new Date().toISOString().slice(0,10)}.${type.includes('mp4')?'mp4':'webm'}`;
  link.textContent='Download recording';
  const controls=document.createElement('div');
  controls.className='take-controls';
  const title=document.createElement('input');
  title.type='text';
  title.maxLength=100;
  title.value=`Lincoln violin practice — ${session?.item.technique ?? 'practice'} — ${new Date().toLocaleDateString()}`;
  title.setAttribute('aria-label','YouTube video title');
  const descriptionLabel=document.createElement('label');
  descriptionLabel.textContent='YouTube description (edit before upload)';
  const description=document.createElement('textarea');
  description.rows=5;
  description.maxLength=5000;
  description.setAttribute('aria-label','YouTube video description');
  description.value=session
    ? [`Lincoln violin practice: ${session.item.technique}.`,
      `Practice mode: ${PRACTICE_MODES[session.mode].label}.`,
      `Reference video: ${session.video.url}`,
      `Assigned timestamps: ${formatClipRange(session.start,session.end)}.`,
      `Assigned practice count: ${session.repeats} repetitions.`].join('\n')
    : 'Lincoln violin practice.';
  const playlistControls=document.createElement('div');
  playlistControls.className='playlist-controls';
  const playlistLabel=document.createElement('label');playlistLabel.textContent='YouTube playlist';
  const playlistSelect=document.createElement('select');
  playlistSelect.setAttribute('aria-label','YouTube playlist');
  const noPlaylist=document.createElement('option');noPlaylist.value='';noPlaylist.textContent='Don’t add to a playlist';
  playlistSelect.append(noPlaylist);
  const loadPlaylists=document.createElement('button');
  loadPlaylists.type='button';loadPlaylists.textContent='Load my playlists';
  loadPlaylists.addEventListener('click',async()=>{
    loadPlaylists.disabled=true;status.textContent='Requesting playlist access…';
    try{
      const playlists=ownedPlaylists ?? await fetchOwnedPlaylists();
      playlistSelect.replaceChildren(noPlaylist);
      for(const playlist of playlists){
        const option=document.createElement('option');option.value=playlist.id;option.textContent=playlist.title;playlistSelect.append(option);
      }
      status.textContent=playlists.length?'Choose a playlist, then upload.':'No playlists found on this YouTube channel.';
    }catch(error){status.textContent=error.message || 'Could not load your playlists.';}
    finally{loadPlaylists.disabled=false;}
  });
  playlistSelect.addEventListener('change',()=>{upload.textContent=playlistSelect.value?'Upload as Private + Add to Playlist':'Upload as Private';});
  playlistLabel.append(playlistSelect);
  playlistControls.append(playlistLabel,loadPlaylists);
  const upload=document.createElement('button');
  upload.type='button';
  upload.className='upload-button';
  upload.textContent='Upload as Private';
  const status=document.createElement('p');
  status.className='upload-status';
  status.setAttribute('role','status');
  upload.addEventListener('click',async()=>{
    upload.disabled=true;
    status.textContent='Requesting Google sign-in…';
    try{
      const playlistId=playlistSelect.value;
      const selectedPlaylist=ownedPlaylists?.find(playlist=>playlist.id===playlistId);
      const {id,playlistError}=await uploadVideo(blob,session,title.value,description.value,playlistId);
      const uploadedLink=document.createElement('a');
      uploadedLink.href=`https://youtu.be/${encodeURIComponent(id)}`;
      uploadedLink.target='_blank';
      uploadedLink.rel='noreferrer noopener';
      uploadedLink.textContent='Open private YouTube video';
      status.replaceChildren(document.createTextNode(playlistError
        ? `Uploaded as Private, but could not add to ${selectedPlaylist?.title || 'the playlist'}: ${playlistError} `
        : playlistId ? `Uploaded as Private and added to ${selectedPlaylist?.title || 'the playlist'}. ` : 'Uploaded as Private. '),uploadedLink);
      if(playlistError && playlistId){
        const retry=document.createElement('button');retry.type='button';retry.textContent='Retry adding to playlist';
        retry.addEventListener('click',async()=>{
          retry.disabled=true;status.append(document.createTextNode(' Retrying…'));
          try{
            const token=await requestUploadAccess();
            await addVideoToPlaylist(id,playlistId,token);
            retry.remove();
            status.replaceChildren(document.createTextNode(`Uploaded as Private and added to ${selectedPlaylist?.title || 'the playlist'}. `),uploadedLink);
            dailyState?.practiceLog.push({taskTitle:session?.item.technique ?? 'Violin practice',event:`Added to ${selectedPlaylist?.title || 'playlist'}`,at:new Date().toISOString(),videoId:id});
            renderSummary();saveDailyState();
          }catch(error){status.append(document.createTextNode(` Retry failed: ${error.message || 'YouTube could not add the video.'}`));retry.disabled=false;}
        });
        status.append(' ',retry);
      }
      upload.textContent='Uploaded';
      dailyState?.practiceLog.push({taskTitle:session?.item.technique ?? 'Violin practice',event:playlistId&&!playlistError?`Uploaded as Private to ${selectedPlaylist?.title || 'playlist'}`:'Uploaded as Private',at:new Date().toISOString(),videoId:id});
      renderSummary();saveDailyState();
    }catch(error){
      status.textContent=error.message || 'Upload failed. Try again.';
      upload.disabled=false;
    }
  });
  controls.append(title,descriptionLabel,description,playlistControls,upload,status);
  take.append(video,link,controls);
  savedTakes.append(take);
  if(session && dailyState){
    dailyState.practiceLog.push({taskTitle:session.item.technique,mode:PRACTICE_MODES[session.mode].label,event:'Recording saved',at:new Date().toISOString()});
    renderSummary();saveDailyState();
  }
  if(youtubeConfig.clientId)loadGoogleIdentity().catch(()=>{});
}

function handlePlayerState(state){
  if(state!==0 || !activeSession || activeSession.mode==='solo')return;
  activeSession.remaining--;
  if(activeSession.remaining>0){
    sessionProgress.textContent=`${activeSession.remaining} plays left`;
    playAssignedClip(ytPlayer,activeSession);
    return;
  }
  sessionProgress.textContent=`${activeSession.repeats} of ${activeSession.repeats} plays`;
  recordingStatus.textContent=activeSession.mode==='listen'
    ? 'Listening complete.'
    : 'Practice complete. Review or download Lincoln’s recording below.';
  if(recorder?.state==='recording')recorder.stop();
  releaseCamera();
  const completedId=activeSession.item.id;
  activeSession=null;
  markTaskComplete(completedId);
}

function releaseCamera(){
  window.clearTimeout(recordingTimer);
  if(cameraStream){cameraStream.getTracks().forEach(track=>track.stop());cameraStream=null;}
  cameraPreview.srcObject=null;
  cameraPreview.hidden=true;
  stopRecordingButton.hidden=true;
}

function cancelSession(){
  window.clearTimeout(recordingTimer);
  activeSession=null;
  if(recorder?.state==='recording'){
    recorder.onstop=null;
    recorder.stop();
  }
  recorder=null;
  releaseCamera();
  if(ytPlayer?.pauseVideo)ytPlayer.pauseVideo();
  recordingArea.hidden=true;
}

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

function renderCurrentProgram(program) {
  currentProgramPieces.replaceChildren();
  currentProgram.hidden=!program;
  if(!program)return;
  currentProgramTitle.textContent=program.title;
  currentProgramIntro.textContent=program.intro;
  for(const piece of program.pieces){
    const card=document.createElement('article');
    card.className='repertoire-card';
    if(piece.highlighted)card.classList.add('is-highlighted');

    if(piece.highlighted){
      const badge=document.createElement('span');
      badge.className='focus-badge';
      badge.textContent='Current focus';
      card.append(badge);
    }

    const title=document.createElement('h3');
    title.textContent=piece.title;
    card.append(title);

    const part=document.createElement('p');
    part.className='repertoire-part';
    part.textContent=piece.part;
    card.append(part);

    const resources=document.createElement('ul');
    resources.className='repertoire-resources';
    const sheetLink=privateFileLink(piece.sheet_music);
    if(sheetLink){
      const item=document.createElement('li');
      item.append(sheetLink);
      resources.append(item);
    }
    for(const reference of piece.reference_videos){
      const link=videoLink(reference);
      if(!link)continue;
      const item=document.createElement('li');
      item.append(link);
      resources.append(item);
    }
    card.append(resources);

    if(piece.practice_points.length){
      const audition=document.createElement('section');
      audition.className='audition-practice';
      const label=document.createElement('h4');
      label.textContent='Seating audition practice';
      audition.append(label);
      const points=document.createElement('ul');
      points.className='audition-links';
      for(const point of piece.practice_points){
        const link=videoLink(point);
        if(!link)continue;
        const item=document.createElement('li');
        item.append(link);
        points.append(item);
      }
      audition.append(points);
      card.append(audition);
    }

    currentProgramPieces.append(card);
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
    current_program:null,
    unassigned_materials:[],
    items:[],
  };
  const items=Array.isArray(source.items) ? source.items : [];
  practiceItems=items;
  practiceTitle.textContent=source.title;
  practiceIntro.textContent=source.intro;
  practiceUpdated.textContent=source.updated ? `Updated ${source.updated}` : '';
  practiceCount.textContent=`${items.length} focus ${items.length===1 ? 'area' : 'areas'}`;
  practiceList.replaceChildren();
  renderCurrentProgram(source.current_program);
  showPracticePane('today');
  renderPracticeCards(dailyState ? assignedItems() : []);
  loadDailyState();

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
  window.clearInterval(timerHandle);timerHandle=0;
  dailyState=null;practiceItems=[];customTasks=[];
  practiceTitle.textContent='';
  practiceIntro.textContent='';
  practiceUpdated.textContent='';
  practiceCount.textContent='';
  practiceList.replaceChildren();
  cancelSession();
  practiceCards.replaceChildren();
  practiceRunner.hidden=true;
  practiceSession.hidden=true;
  savedTakes.replaceChildren();
  youtubePlayerHost.replaceChildren();
  ytPlayer=null;
  ytApiPromise=null;
  currentProgramTitle.textContent='';
  currentProgramIntro.textContent='';
  currentProgramPieces.replaceChildren();
  currentProgram.hidden=true;
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
  pauseDailyTimer();
  saveDailyState();
  archivePassword='';
  uploadAccessToken='';
  uploadTokenExpiresAt=0;
  ownedPlaylists=null;
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

function display(payload,password) {
  archivePassword=password;
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
    display(payload,password);
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
document.getElementById('show-today').addEventListener('click',()=>showPracticePane('today'));
document.getElementById('show-assignment').addEventListener('click',()=>showPracticePane('assignment'));
document.getElementById('show-summary').addEventListener('click',()=>showPracticePane('summary'));
document.getElementById('save-assignment').addEventListener('click',saveAssignment);
startPracticeButton.addEventListener('click',startDailyTimer);
pausePracticeButton.addEventListener('click',pauseDailyTimer);
customTaskForm.addEventListener('submit',event=>{
  event.preventDefault();
  const name=document.getElementById('custom-task-name').value.trim();
  const url=document.getElementById('custom-task-url').value.trim();
  const start=parseVideoTime(document.getElementById('custom-task-start').value || '0:00');
  const endText=document.getElementById('custom-task-end').value.trim();
  const end=endText ? parseVideoTime(endText) : null;
  const repeats=Number(document.getElementById('custom-task-repeats').value);
  let validUrl=false;
  try{validUrl=new URL(url).protocol==='https:' && Boolean(youtubeId(url));}catch{}
  if(!validUrl || !Number.isFinite(start) || (end!==null && (!Number.isFinite(end) || end<=start)) || !Number.isInteger(repeats) || repeats<1 || repeats>20){
    assignmentMessage.textContent='Enter a valid YouTube link, valid timestamps, and 1–20 repetitions. Leave the end time blank for the whole video.';
    return;
  }
  customTasks.push({id:`custom-${crypto.randomUUID()}`,title:name,description:'Custom practice task',coachFocus:'',url,label:'Custom YouTube reference',start,end,repeats,custom:true});
  assignmentMessage.textContent='Task added. Save today’s assignment when you are ready.';
  customTaskForm.reset();document.getElementById('custom-task-repeats').value='3';
  renderCustomTaskList();
});
stopRecordingButton.addEventListener('click',()=>{
  window.clearTimeout(recordingTimer);
  if(activeSession?.mode==='solo'){
    finishSoloAttempt(activeSession,false);
  }else{
    if(recorder?.state==='recording')recorder.stop();
    recordingStatus.textContent='Recording stopped.';
    releaseCamera();
  }
});
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
