/**
 * JH2 Mega SDK shadow adapter. Standalone, loopback-only, OFF by default.
 * Does not import Jason Home 2 code or send any light-control commands.
 */
import { createServer } from 'node:http';
import { timingSafeEqual } from 'node:crypto';
import { mkdir, chmod } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { EufyMega, FileSessionStore, LoginStatus } from '@mega-yfue/eufy-sdk';

export const SDK_PACKAGE = '@mega-yfue/eufy-sdk';
export const SDK_VERSION = '0.5.0';
const PORT = Number(process.env.JH2_MEGA_SHADOW_PORT || 48721);
const TOKEN = String(process.env.JH2_MEGA_SHADOW_TOKEN || '');
const ALLOW_LOGIN = process.env.JH2_MEGA_ALLOW_LOGIN === '1';
const EMAIL = String(process.env.EUFY_EMAIL || '');
const PASSWORD = String(process.env.EUFY_PASSWORD || '');
const COUNTRY = String(process.env.EUFY_COUNTRY_CODE || 'US');
const OPENUDID = String(process.env.JH2_MEGA_SHADOW_OPENUDID || '');
const SESSION_FILE = resolve(process.env.JH2_MEGA_SESSION_FILE || './private/.eufy-shadow-session.json');
const REQUIRE_AUTH = TOKEN.length >= 24;
let client = null, state = 'not-started', lastLoginAt = 0;
let lastError = null, challenge = null, busy = false;

function constantTimeAuth(input) {
  if (!REQUIRE_AUTH || !input) return false;
  const a = Buffer.from(input), b = Buffer.from(TOKEN);
  return a.length === b.length && timingSafeEqual(a, b);
}
function publicStatus() {
  return { ok: true, mode: 'shadow-read-only', package: SDK_PACKAGE,
    sdkVersion: SDK_VERSION, loginAllowed: ALLOW_LOGIN,
    configured: !!(EMAIL && PASSWORD && OPENUDID && REQUIRE_AUTH),
    state, lastError, challenge: challenge?.type || null,
    writesEnabled: false, transportOwner: 'separate-sdk', lightsModified: false };
}
function reply(res, code, data) {
  const body = JSON.stringify(data);
  res.writeHead(code, { 'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store', 'content-length': Buffer.byteLength(body),
    'x-content-type-options': 'nosniff' });
  res.end(body);
}
async function parseBody(req) {
  let size = 0, chunks = [];
  for await (const part of req) {
    size += part.length;
    if (size > 4096) throw Object.assign(new Error('Request too large'), {status: 413});
    chunks.push(part);
  }
  const body = Buffer.concat(chunks).toString('utf8');
  return body ? JSON.parse(body) : {};
}
function loginStatus(result) {
  if (result?.status === LoginStatus.Ok) {
    state = 'connected'; challenge = null; lastError = null;
    return { status: 'connected' };
  }
  if (result?.status === LoginStatus.Captcha) {
    state = 'needs-captcha'; challenge = { type: 'captcha', image: result.image || null };
    return { status: state, captchaImage: challenge.image };
  }
  if (result?.status === LoginStatus.TwoFactor) {
    state = 'needs-2fa'; challenge = { type: '2fa' };
    return { status: state, method: result.method || null };
  }
  state = 'login-incomplete';
  return { status: state, step: String(result?.status || 'unknown') };
}
async function login() {
  if (!ALLOW_LOGIN) return { code: 403, body: { error: 'SDK login disabled until intentionally enabled' } };
  if (!EMAIL || !PASSWORD || !OPENUDID) return { code: 409, body: { error: 'Eufy shadow environment not configured' } };
  if (busy) return { code: 409, body: { error: 'Login already running' } };
  const now = Date.now();
  if (now - lastLoginAt < 5 * 60 * 1000) return { code: 429, body: { error: 'Login rate limited (5 minutes)' } };
  lastLoginAt = now; busy = true;
  try {
    if (!client) {
      await mkdir(dirname(SESSION_FILE), { recursive: true, mode: 0o700 });
      try { await chmod(dirname(SESSION_FILE), 0o700); } catch {}
      client = new EufyMega({ email: EMAIL, password: PASSWORD,
        countryCode: COUNTRY, openudid: OPENUDID, store: new FileSessionStore(SESSION_FILE) });
      client.on('error', e => { lastError = 'SDK runtime error (details withheld)'; });
      client.on('sessionExpired', () => { state = 'session-expired'; });
    }
    state = 'authenticating';
    return { code: 200, body: { ok: true, ...loginStatus(await client.login()) } };
  } catch (error) {
    state = 'login-failed'; lastError = 'SDK login error (details withheld)';
    return { code: 502, body: { ok: false, error: lastError } };
  } finally { busy = false; }
}
async function answerChallenge(type, answer) {
  if (!client || challenge?.type !== type || state !== (type === '2fa' ? 'needs-2fa' : 'needs-captcha'))
    return { code: 409, body: { error: 'No pending verification of this type' } };
  if (busy) return { code: 409, body: { error: 'Verification already in progress' } };
  if (typeof answer !== 'string' || answer.length < 1 || answer.length > 128)
    return { code: 400, body: { error: 'Invalid verification answer' } };
  busy = true;
  try {
    const result = type === '2fa' ? await client.submitVerifyCode(answer) : await client.solveCaptcha(answer);
    return { code: 200, body: { ok: true, ...loginStatus(result) } };
  } catch { state = 'verification-failed'; return { code: 502, body: { error: 'Verification failed; inspect the SDK flow locally' } }; }
  finally { busy = false; }
}
async function devices() {
  if (state !== 'connected' || !client) return { code: 409, body: { error: 'Shadow SDK is not connected' } };
  const inventory = await client.getDevices();
  const lights = [], unresolved = [];
  for (const d of inventory) {
    // Device.info() exposes safe local metadata; serial numbers are never returned or logged.
    const info = d?.info?.() || {};
    const model = String(info.model || d?.model || '');
    if (!/^T8L/i.test(model) && !d?.smartLight) continue;
    const sn = info.serialNumber || d?.sn;
    let instance = d;
    if (!instance?.smartLight && typeof sn === 'string' && sn.length) {
      try { instance = await client.getDevice(sn); } catch { unresolved.push(model || 'unknown'); continue; }
    }
    const light = instance?.smartLight?.();
    if (!light) { unresolved.push(model || 'unknown'); continue; }
    const boundInfo=instance?.info?.() || info;
    lights.push({ model: boundInfo.model || model || null,
      capabilities: Array.isArray(instance.capabilities) ? instance.capabilities.filter(v=>typeof v==='string').slice(0,20) : [],
      power: light.power ?? null, brightness: light.brightness ?? null,
      effectId: light.effectId ?? null, cloudEffectId: light.cloudEffectId ?? null,
      // No device SN, email, token, account identifiers or network addresses in API response.
      canSetEffect: typeof light.setEffect === 'function',
      canSetColor: typeof light.setColor === 'function' });
  }
  return { code: 200, body: { ok: true, mode: 'read-only', count: lights.length, lights,
    unboundLightModels: unresolved, reportedTotalDevices: inventory.length } };
}
const server = createServer(async (req, res) => {
  try {
    const path = new URL(req.url || '/', 'http://localhost').pathname;
    if (req.method === 'GET' && path === '/health')
      return reply(res, 200, { ...publicStatus(), configured: false, lastError: null, challenge: null });
    const auth = String(req.headers.authorization || '');
    if (!auth.startsWith('Bearer ') || !constantTimeAuth(auth.slice(7)))
      return reply(res, 401, { ok: false, error: 'Bearer token required' });
    if (req.method === 'GET' && path === '/v1/status') return reply(res, 200, publicStatus());
    if (req.method === 'GET' && path === '/v1/devices') {
      const result = await devices(); return reply(res, result.code, result.body);
    }
    if (req.method === 'POST' && path === '/v1/login') {
      const result = await login(); return reply(res, result.code, result.body);
    }
    if (req.method === 'POST' && (path === '/v1/captcha' || path === '/v1/2fa')) {
      const body = await parseBody(req);
      const result = await answerChallenge(path === '/v1/captcha' ? 'captcha' : '2fa', body.answer);
      return reply(res, result.code, result.body);
    }
    reply(res, 404, {ok: false, error: 'Unknown read-only SDK lab route'});
  } catch (error) { reply(res, error?.status || 502, { ok: false, error: 'Shadow service request failed' }); }
});
if (!Number.isInteger(PORT) || PORT < 1024 || PORT > 65535) throw Error('Invalid shadow port');
server.listen(PORT, '127.0.0.1', () => {
  console.log(JSON.stringify({ service: 'JH2 Eufy Mega SDK shadow', port: PORT,
    sdkVersion: SDK_VERSION, listen: '127.0.0.1', readOnly: true, loginAllowed: ALLOW_LOGIN }));
});
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => {
  server.close();
  void client?.disconnect?.().finally(() => process.exit(0));
  if (!client) process.exit(0);
});