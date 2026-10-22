'use strict';

// Keep React Native's own build/Metro/device/launch behavior, changing only the
// adb transport for a single-device install. Hooks live in this CLI process;
// project sources, the SDK and child Node processes are never modified.
const path = require('node:path');
const fs = require('node:fs');

function installationArgs(executable, args) {
  if (typeof executable !== 'string' || !Array.isArray(args) ||
      !/^adb(?:\.exe)?$/i.test(path.win32.basename(path.posix.basename(executable)))) return args;
  // adb's global options precede the command. A serial itself may be 'install'.
  let index = 0;
  while (index < args.length) {
    if (['-s', '-t', '-H', '-P', '-L', '--one-device'].includes(args[index])) index += 2;
    else if (['-d', '-e', '-a', '--exit-on-write-error'].includes(args[index])) index++;
    else break;
  }
  if (args[index] !== 'install' || args.includes('--no-streaming')) return args;
  return [...args.slice(0, index + 1), '--no-streaming', ...args.slice(index + 1).filter(arg => arg !== '--streaming')];
}

function installAdbHooks(childProcess, log = message => process.stderr.write(message + '\n')) {
  for (const method of ['spawn', 'spawnSync', 'execFile', 'execFileSync']) {
    const original = childProcess[method];
    childProcess[method] = function (file, args, ...rest) {
      const rewritten = installationArgs(file, args);
      if (rewritten !== args) log('[RN Workbench] Installing with adb --no-streaming on the selected device.');
      return Reflect.apply(original, this, [file, rewritten, ...rest]);
    };
  }
}

function selectedSerial(args) {
  for (let index = 0; index < args.length; index++) {
    if (args[index] === '--device' || args[index] === '--deviceId') return args[index + 1];
    if (/^--device(?:Id)?=/.test(args[index])) return args[index].slice(args[index].indexOf('=') + 1);
  }
}

function ensureDeviceAvailable(childProcess, adb, serial) {
  if (!serial) throw new Error('No Android device serial was specified. Refresh devices and select a target.');
  let state;
  try {
    state = childProcess.execFileSync(adb, ['-s', serial, 'get-state'], {
      encoding: 'utf8', timeout: 10_000, stdio: ['ignore', 'pipe', 'pipe'],
    }).trim();
  } catch (error) {
    throw new Error(`Selected Android device '${serial}' is unavailable. Refresh devices and select it again. ${String(error.stderr || error.message).trim()}`);
  }
  if (state !== 'device') throw new Error(`Selected Android device '${serial}' is ${state || 'unavailable'}. Check USB debugging authorization.`);
}

async function run() {
  const childProcess = require('node:child_process');
  const adb = process.env.RN_WORKBENCH_ADB || (process.env.ANDROID_HOME ? path.join(process.env.ANDROID_HOME, 'platform-tools', 'adb') : 'adb');
  ensureDeviceAvailable(childProcess, adb, selectedSerial(process.argv.slice(2)));
  const manifest = require.resolve('react-native/package.json', { paths: [process.cwd()] });
  const pkg = JSON.parse(fs.readFileSync(manifest, 'utf8'));
  const bin = typeof pkg.bin === 'string' ? pkg.bin : pkg.bin?.['react-native'];
  if (!bin) throw new Error('The local react-native package does not declare a CLI entry point.');
  const cli = path.resolve(path.dirname(manifest), bin);
  installAdbHooks(childProcess);
  // Commander and React Native see the same argv as a direct CLI invocation.
  process.argv[1] = cli;
  const entry = require(cli);
  if (typeof entry.run !== 'function') throw new Error('The local React Native CLI does not export run().');
  await entry.run(pkg.name);
}

module.exports = { installationArgs, installAdbHooks, selectedSerial, ensureDeviceAvailable };
if (require.main === module) run().catch(error => { console.error(error); process.exitCode = 1; });
