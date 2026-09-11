const encoder = new TextEncoder();
const AAD = encoder.encode('lincoln-family:v2');
export const ITERATIONS = 600000;

function base64(bytes) {
  let text='';
  for (const byte of new Uint8Array(bytes)) text+=String.fromCharCode(byte);
  return btoa(text);
}
function bytes(text) {
  return Uint8Array.from(atob(text), char=>char.charCodeAt(0));
}
async function keyFor(password, salt) {
  const material=await crypto.subtle.importKey('raw',encoder.encode(password),'PBKDF2',false,['deriveKey']);
  return crypto.subtle.deriveKey({name:'PBKDF2',salt,iterations:ITERATIONS,hash:'SHA-256'},material,{name:'AES-GCM',length:256},false,['encrypt','decrypt']);
}
export async function encrypt(payload,password) {
  if (typeof password !== 'string' || password.length<4) throw new Error('Use a passcode of at least 4 characters.');
  const salt=crypto.getRandomValues(new Uint8Array(16));
  const iv=crypto.getRandomValues(new Uint8Array(12));
  const ciphertext=await crypto.subtle.encrypt({name:'AES-GCM',iv,additionalData:AAD,tagLength:128},await keyFor(password,salt),encoder.encode(JSON.stringify(payload)));
  return {version:2,kdf:'PBKDF2-SHA256',iterations:ITERATIONS,cipher:'AES-256-GCM',salt:base64(salt),iv:base64(iv),ciphertext:base64(ciphertext)};
}
export async function decrypt(envelope,password) {
  if (envelope.version!==2 || envelope.iterations!==ITERATIONS || envelope.kdf!=='PBKDF2-SHA256' || envelope.cipher!=='AES-256-GCM') throw new Error('Unsupported family archive format.');
  const salt=bytes(envelope.salt),iv=bytes(envelope.iv);
  if (salt.length!==16 || iv.length!==12) throw new Error('Invalid family archive format.');
  const plaintext=await crypto.subtle.decrypt({name:'AES-GCM',iv,additionalData:AAD,tagLength:128},await keyFor(password,salt),bytes(envelope.ciphertext));
  const payload=JSON.parse(new TextDecoder().decode(plaintext));
  const calendar=payload?.calendar;
  const book=payload?.book;
  const calendarValid=calendar && ['html','ics','agenda'].every(name=>typeof calendar[name]==='string');
  const bookValid=book
    && ['title','subtitle','edition','filename','pdfBase64'].every(name=>typeof book[name]==='string')
    && book.filename.endsWith('.pdf')
    && book.pdfBase64.length>8
    && /^[A-Za-z0-9+/]*={0,2}$/.test(book.pdfBase64);
  if (payload?.version!==2 || !calendarValid || !bookValid) throw new Error('Invalid family content.');
  return payload;
}
