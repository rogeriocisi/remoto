// Prints every Fig*.html of a folder to a vector PDF with a headless Chrome or Edge.
// Usage: node pdf.js <folder with the Fig*.html written by figs.js>
// Set the environment variable CHROME to the browser executable if it is not found.
const { spawnSync } = require('child_process');
const fs = require('fs'), path = require('path'), os = require('os');
const { pathToFileURL } = require('url');

const dir = path.resolve(process.argv[2] || '.');
const candidates = [
  process.env.CHROME,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser',
].filter(Boolean);
const exe = candidates.find(p => fs.existsSync(p));
if (!exe) { console.error('No Chrome or Edge found; set CHROME to the executable.'); process.exit(1); }

const pages = fs.readdirSync(dir).filter(f => /^Fig\d+\.html$/.test(f)).sort();
if (!pages.length) { console.error('No Fig*.html in ' + dir + '; run figs.js first.'); process.exit(1); }

// A throw-away profile, so the browser never touches the user's own profile.
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'remoto-fig-'));
let failed = 0;
try {
  for (const page of pages) {
    const pdf = path.join(dir, page.replace(/\.html$/, '.pdf'));
    if (fs.existsSync(pdf)) fs.rmSync(pdf);
    const r = spawnSync(exe, ['--headless=new', '--disable-gpu', '--no-first-run', '--user-data-dir=' + profile,
      '--no-pdf-header-footer', '--print-to-pdf=' + pdf, pathToFileURL(path.join(dir, page)).href], { stdio: 'ignore' });
    const ok = r.status === 0 && fs.existsSync(pdf);
    console.log(page + ' -> ' + (ok ? path.basename(pdf) + ' (' + fs.statSync(pdf).size + ' bytes)' : 'FAILED'));
    if (!ok) failed++;
  }
} finally {
  fs.rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
}
process.exit(failed ? 1 : 0);
