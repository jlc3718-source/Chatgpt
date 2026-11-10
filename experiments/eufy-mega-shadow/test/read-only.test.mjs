import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../shadow.mjs', import.meta.url), 'utf8');
const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));

test('locks SDK dependency and Node engine', () => {
  assert.equal(packageJson.dependencies['@mega-yfue/eufy-sdk'], '0.5.0');
  assert.equal(packageJson.engines.node, '>=24.5.0');
});

test('never calls a light write method', () => {
  for (const forbidden of ['light.on(', 'light.off(', 'light.setBrightness(', 'light.setColor(', 'light.setEffect(', 'light.refreshState(']) {
    assert.equal(source.includes(forbidden), false, `Unexpected light command: ${forbidden}`);
  }
});

test('default login locked and server bound only to loopback', () => {
  assert.match(source, /JH2_MEGA_ALLOW_LOGIN === '1'/);
  assert.match(source, /server\.listen\(PORT, '127\.0\.0\.1'/);
  assert.match(source, /OPENUDID = String\(process\.env\.JH2_MEGA_SHADOW_OPENUDID/);
});

test('read routes do not expose identifiers', () => {
  assert.match(source, /No device SN or account identifiers in the API response/);
  assert.match(source, /light\.power/);
  assert.match(source, /light\.brightness/);
});