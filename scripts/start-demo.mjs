import { spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const args = process.argv.slice(2);
if (args.some(arg => /^(--lan|--tunnel|--host)(=|$)/.test(arg))) {
  throw new Error('The work report demo runs on localhost.');
}
const reports = await readFile(new URL('../supabase/november-work.json', import.meta.url), 'utf8').catch(error => {
  if (error.code !== 'ENOENT') throw error;
  return null;
});

const expo = spawn(process.execPath, [require.resolve('expo/bin/cli'), 'start', '--localhost', ...args.filter(arg => arg !== '--localhost')], {
  cwd: fileURLToPath(new URL('../', import.meta.url)),
  env: {
    ...process.env,
    EXPO_NO_DOTENV: '1',
    EXPO_PUBLIC_DAYTABS_STORAGE: 'local',
    EXPO_PUBLIC_SUPABASE_URL: '',
    EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY: '',
    EXPO_PUBLIC_DAYTABS_LOCAL_REPORT: reports ? JSON.stringify(JSON.parse(reports)) : '',
  },
  stdio: 'inherit',
});

expo.on('exit', code => { process.exitCode = code ?? 1; });
