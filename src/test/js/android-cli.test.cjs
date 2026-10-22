'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { installationArgs, installAdbHooks, selectedSerial, ensureDeviceAvailable } = require('../../main/resources/runner/android-cli.cjs');

test('changes single-device installs on Windows/macOS/Linux without mutating caller args', () => {
  for (const adb of ['adb', 'D:\\Android SDK\\platform-tools\\adb.exe', '/opt/android/sdk/platform-tools/adb']) {
    const original = ['-s', 'phone serial', 'install', '-r', '-d', '/app directory/app.apk'];
    assert.deepEqual(installationArgs(adb, original), ['-s', 'phone serial', 'install', '--no-streaming', '-r', '-d', '/app directory/app.apk']);
    assert.equal(original.length, 6);
  }
});

test('preserves device probes, reverse, launches, other executables and optional argument overloads', () => {
  for (const args of [['devices'], ['-s', 'phone', 'reverse', 'tcp:8081', 'tcp:8081'],
    ['-s', 'phone', 'shell', 'pm', 'install', 'x.apk'], ['-s', 'install', 'get-state'],
    ['-s', 'phone', 'install-multiple', 'a.apk', 'b.apk'], undefined]) {
    assert.equal(installationArgs('adb', args), args);
  }
  const args = ['install', 'x.apk'];
  assert.equal(installationArgs('npm', args), args);
  assert.equal(installationArgs('my-adb', args), args);
});

test('respects existing no-streaming and handles global host/transport options', () => {
  const existing = ['-s', 'phone', 'install', '--no-streaming', 'x.apk'];
  assert.equal(installationArgs('adb', existing), existing);
  assert.deepEqual(installationArgs('adb', ['-H', 'localhost', '-P', '5037', '-t', '1', 'install', '--streaming', 'x.apk']),
    ['-H', 'localhost', '-P', '5037', '-t', '1', 'install', '--no-streaming', 'x.apk']);
});

test('wraps sync and async child process APIs while preserving options, callbacks and return values', () => {
  const calls = [];
  const options = { cwd: '/app with spaces', env: { ANDROID_SERIAL: 'target' } };
  const callback = () => {};
  const result = { status: 0 };
  const childProcess = Object.fromEntries(['spawn', 'spawnSync', 'execFile', 'execFileSync'].map(method =>
    [method, function (...args) { assert.equal(this, childProcess); calls.push([method, ...args]); return result; }]));
  const messages = [];
  installAdbHooks(childProcess, message => messages.push(message));
  for (const method of Object.keys(childProcess)) {
    assert.equal(childProcess[method]('adb', ['-s', 'target', 'install', '-r', 'app.apk'], options, callback), result);
  }
  assert.equal(messages.length, 4);
  for (const [, file, args, opts, cb] of calls) {
    assert.equal(file, 'adb');
    assert.deepEqual(args, ['-s', 'target', 'install', '--no-streaming', '-r', 'app.apk']);
    assert.equal(opts, options);
    assert.equal(cb, callback);
  }
});

test('finds modern and legacy target serials, including equals form', () => {
  assert.equal(selectedSerial(['run-android', '--device', 'modern']), 'modern');
  assert.equal(selectedSerial(['run-android', '--deviceId=legacy']), 'legacy');
  assert.equal(selectedSerial(['run-android', '--deviceId', 'old']), 'old');
  assert.equal(selectedSerial(['run-android', '--device=equal']), 'equal');
  assert.equal(selectedSerial(['run-android']), undefined);
});

test('fails on missing, offline or unauthorized devices before invoking the CLI', () => {
  const calls = [];
  const childProcess = { execFileSync(adb, args, options) {
    calls.push({ adb, args, options });
    return 'device\n';
  } };
  ensureDeviceAvailable(childProcess, '/sdk/adb', 'target');
  assert.deepEqual(calls[0].args, ['-s', 'target', 'get-state']);
  assert.equal(calls[0].adb, '/sdk/adb');
  assert.equal(calls[0].options.timeout, 10_000);
  assert.throws(() => ensureDeviceAvailable(childProcess, 'adb', undefined), /No Android device serial/);
  for (const state of ['offline', 'unauthorized', '']) {
    assert.throws(() => ensureDeviceAvailable({ execFileSync: () => state }, 'adb', 'target'), /Check USB debugging/);
  }
  assert.throws(() => ensureDeviceAvailable({ execFileSync() { throw { stderr: 'device not found' }; } }, 'adb', 'target'), /Refresh devices.*device not found/);
});
