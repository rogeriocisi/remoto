// SHA-256 of every output of a run, computed on the decompressed CSV (the .gz files themselves
// may differ between machines). Usage: node hash-run.js <run folder> [file with expected hashes]
// With a second argument, compares instead of printing and exits with 1 on any difference.
const fs = require('fs'), path = require('path'), zlib = require('zlib'), crypto = require('crypto');

const dir = path.join(path.resolve(process.argv[2] || '.'), 'results');
const lines = fs.readdirSync(dir).filter(f => f.endsWith('.csv.gz')).sort().map(f =>
  crypto.createHash('sha256').update(zlib.gunzipSync(fs.readFileSync(path.join(dir, f)))).digest('hex') + '  ' + f);

if (!process.argv[3]) { console.log(lines.join('\n')); process.exit(0); }

const expected = fs.readFileSync(process.argv[3], 'utf8').split(/\r?\n/).filter(l => l && !l.startsWith('#'));
const want = new Map(expected.map(l => [l.slice(66), l.slice(0, 64)]));
const got = new Map(lines.map(l => [l.slice(66), l.slice(0, 64)]));
let diff = 0;
for (const [name, hash] of want) {
  if (!got.has(name)) { console.log('missing   ' + name); diff++; } else if (got.get(name) !== hash) { console.log('differs   ' + name); diff++; }
}
for (const name of got.keys()) if (!want.has(name)) { console.log('extra     ' + name); diff++; }
console.log(diff ? diff + ' difference(s)' : 'identical: ' + got.size + ' outputs');
process.exit(diff ? 1 : 0);
