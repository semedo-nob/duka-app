import { readStaff, saveStaff, type StaffRecord } from './localDb';

function bytesToB64(bytes: Uint8Array) {
  let text = '';
  for (const byte of bytes) text += String.fromCharCode(byte);
  return btoa(text);
}

function b64ToBytes(value: string) {
  const text = atob(value);
  const bytes = new Uint8Array(text.length);
  for (let i = 0; i < text.length; i += 1) bytes[i] = text.charCodeAt(i);
  return bytes;
}

async function derive(pin: string, salt: Uint8Array) {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(pin), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', salt: salt as BufferSource, iterations: 120_000, hash: 'SHA-256' }, key, 256);
  return bytesToB64(new Uint8Array(bits));
}

export async function rememberStaffPin(user: { id: number; name: string; phone: string; role: string }, pin: string) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const record: StaffRecord = {
    phone: user.phone.replace(/\s+/g, ''),
    userId: user.id,
    name: user.name,
    role: user.role,
    salt: bytesToB64(salt),
    verifier: await derive(pin, salt),
  };
  await saveStaff(record);
}

export async function unlockOffline(phone: string, pin: string) {
  const record = await readStaff(phone);
  if (!record) return null;
  const verifier = await derive(pin, b64ToBytes(record.salt));
  if (verifier !== record.verifier) return null;
  return { id: record.userId, name: record.name, phone: record.phone, role: record.role, offline: true as const };
}
