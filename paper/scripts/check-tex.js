// Static checks of the manuscript, since no TeX is installed here.
const fs = require('fs'), path = require('path');
const dir = process.argv[2];
const tex = fs.readFileSync(path.join(dir, 'remoto-2026.tex'), 'utf8');
const bib = fs.readFileSync(path.join(dir, 'references.bib'), 'utf8');
let problems = 0; const bad = m => { problems++; console.log('PROBLEM: ' + m); };

// 1. non-ASCII characters
for (const [name, txt] of [['tex', tex], ['bib', bib]]) {
  txt.split('\n').forEach((l, i) => { const m = l.match(/[^\x00-\x7F]/); if (m) bad(`${name} line ${i + 1}: non-ASCII character "${m[0]}"`); });
}
// strip comments (unescaped %) and verbatim blocks for structural checks
const lines = tex.split('\n'); let inVerb = false; const code = [];
for (const l of lines) {
  if (l.includes('\\begin{verbatim}')) { inVerb = true; code.push(''); continue; }
  if (l.includes('\\end{verbatim}')) { inVerb = false; code.push(''); continue; }
  if (inVerb) { code.push(''); continue; }
  code.push(l.replace(/(^|[^\\])%.*$/, '$1'));
}
const body = code.join('\n');
// 2. brace balance (ignoring \{ and \})
let depth = 0, minDepth = 0; const t = body.replace(/\\[{}]/g, '');
t.split('\n').forEach((l, i) => { for (const ch of l) { if (ch === '{') depth++; else if (ch === '}') { depth--; if (depth < 0) { bad(`line ${i + 1}: closing brace without opening`); depth = 0; } } } });
if (depth !== 0) bad(`unbalanced braces: depth ${depth} at end of file`);
// 3. environments
const stack = []; const re = /\\(begin|end)\{([^}]+)\}/g; let m;
const lineOf = idx => body.slice(0, idx).split('\n').length;
while ((m = re.exec(body))) { if (m[1] === 'begin') stack.push([m[2], lineOf(m.index)]); else { const top = stack.pop(); if (!top || top[0] !== m[2]) bad(`line ${lineOf(m.index)}: \\end{${m[2]}} does not match ${top ? '\\begin{' + top[0] + '} of line ' + top[1] : 'anything'}`); } }
for (const [e, l] of stack) bad(`\\begin{${e}} of line ${l} is never closed`);
// 4. math delimiters: even number of unescaped $ per paragraph
body.split(/\n\s*\n/).forEach(p => { const c = (p.replace(/\\\$/g, '').match(/\$/g) || []).length; if (c % 2) bad('odd number of $ in paragraph starting: ' + p.trim().slice(0, 60)); });
// 5. labels and references
const labels = new Set(); const dup = [];
for (const x of body.matchAll(/\\label\{([^}]+)\}/g)) { if (labels.has(x[1])) dup.push(x[1]); labels.add(x[1]); }
dup.forEach(d => bad('duplicate label ' + d));
const refs = new Set(); for (const x of body.matchAll(/\\(?:eq)?ref\{([^}]+)\}/g)) refs.add(x[1]);
for (const r of refs) if (!labels.has(r)) bad('reference to undefined label ' + r);
const unused = [...labels].filter(l => !refs.has(l));
// 6. citations
const keys = new Set([...bib.matchAll(/^@\w+\{([^,]+),/gm)].map(x => x[1]));
const cited = new Set(); for (const x of body.matchAll(/\\cite[tp]?\{([^}]+)\}/g)) x[1].split(',').forEach(k => cited.add(k.trim()));
for (const k of cited) if (!keys.has(k)) bad('citation of key missing in references.bib: ' + k);
const uncited = [...keys].filter(k => !cited.has(k));
// 7. unescaped specials in text: & outside tabular/align, # , _ outside math and \code
let env = [];
code.forEach((l, i) => {
  for (const b of l.matchAll(/\\begin\{([^}]+)\}/g)) env.push(b[1]);
  const inTab = env.some(e => /tabular|align/.test(e));
  const noMath = l.replace(/\$[^$]*\$/g, '').replace(/\\(?:label|ref|eqref|cite[tp]?|includegraphics(?:\[[^\]]*\])?|bibliography|url)\{[^}]*\}/g, '');
  if (!inTab && /(^|[^\\])&/.test(noMath)) bad(`line ${i + 1}: unescaped & outside a table`);
  if (/(^|[^\\])#/.test(noMath) && !/(new|provide)command/.test(l)) bad(`line ${i + 1}: unescaped #`);
  if (!env.some(e => /align|equation/.test(e)) && /(^|[^\\])_/.test(noMath)) bad(`line ${i + 1}: unescaped _ outside math: ${noMath.match(/.{0,20}[^\\]_.{0,20}/)[0]}`);
  if (!env.some(e => /align|equation/.test(e)) && /(^|[^\\])\^/.test(noMath)) bad(`line ${i + 1}: unescaped ^ outside math`);
  if (/[<>]/.test(noMath)) bad(`line ${i + 1}: < or > in text mode`);
  for (const e of l.matchAll(/\\end\{([^}]+)\}/g)) { const k = env.lastIndexOf(e[1]); if (k >= 0) env.splice(k, 1); }
});
// 7b. a comment that swallowed the rest of a line (an edit inserted "%..." mid-paragraph).
// Runs over the raw lines, before comments are stripped.
let verb = false;
lines.forEach((l, i) => {
  if (l.includes('\\begin{verbatim}')) verb = true;
  if (l.includes('\\end{verbatim}')) verb = false;
  if (verb) return;
  const cm = l.match(/(^|[^\\])%(.*)$/);
  if (!cm) return;
  const after = cm[2];
  if (/\\(cite[tp]?|ref|eqref|includegraphics|label)\{/.test(after) || after.length > 400) {
    bad(`line ${i + 1}: comment looks like it swallowed text (${after.length} chars after %)`);
  }
});

// 8. figures exist
for (const x of body.matchAll(/\\includegraphics(?:\[[^\]]*\])?\{([^}]+)\}/g)) { const p = path.join(dir, 'figures', x[1] + '.pdf'); if (!fs.existsSync(p)) bad('missing figure file ' + p); }
// 9. table rows: same number of columns within each tabular
for (const tb of body.matchAll(/\\begin\{tabular\}\{([^\n]*)\}\n([\s\S]*?)\\end\{tabular\}/g)) {
  const spec = tb[1].replace(/@\{[^}]*\}/g, '').replace(/p\{[^}]*\}/g, 'p'); const ncol = spec.replace(/[^lcrp]/g, '').length;
  tb[2].split('\\\\').map(r => r.replace(/\\(toprule|midrule|botrule)/g, '').trim()).filter(Boolean).forEach(r => {
    const cells = r.replace(/\\&/g, '').split('&').length; if (cells !== ncol) bad(`table row with ${cells} cells in a ${ncol}-column table: ${r.slice(0, 50)}`);
  });
}
const words = body.replace(/\\[a-zA-Z]+(\[[^\]]*\])?/g, ' ').replace(/[{}$\\]/g, ' ').split(/\s+/).filter(w => /[A-Za-z]/.test(w)).length;
console.log(`labels ${labels.size}, refs ${refs.size}, bib keys ${keys.size}, cited ${cited.size}`);
console.log('labels never referenced:', unused.join(', ') || '-');
console.log('bib entries not cited:', uncited.join(', ') || '-');
console.log('TODO(authors) markers:', (tex.match(/TODO\(authors\)/g) || []).length, ' visible \\TODO:', (body.match(/\\TODO\{/g) || []).length - 0);
console.log('approximate words in the body (with captions and tables):', words);
console.log(problems ? `${problems} problem(s)` : 'no structural problems found');
