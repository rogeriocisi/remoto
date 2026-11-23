// Minimal SVG plotting helpers. User units are millimetres.
const FONT = 'Arial, Helvetica, sans-serif';
const FS = 2.8;            // ~8 pt
const LW = 0.2;            // axis line width (mm); journal minimum is 0.1 mm
const BLACK = '#000000', BLUE = '#0072B2', VERM = '#D55E00', GREY = '#666666';

function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
function f(x) { return (Math.round(x * 1000) / 1000).toString(); }

function text(x, y, s, o = {}) {
  const a = o.anchor || 'start', w = o.bold ? ' font-weight="bold"' : '', st = o.italic ? ' font-style="italic"' : '';
  const r = o.rotate ? ` transform="rotate(${o.rotate} ${f(x)} ${f(y)})"` : '';
  return `<text x="${f(x)}" y="${f(y)}" font-family="${FONT}" font-size="${o.size || FS}" text-anchor="${a}"${w}${st}${r} fill="${o.fill || BLACK}">${esc(s)}</text>`;
}

function niceTicks(lo, hi, n) {
  const span = hi - lo, raw = span / n, p = Math.pow(10, Math.floor(Math.log10(raw)));
  const m = raw / p; const step = (m < 1.5 ? 1 : m < 3 ? 2 : m < 7 ? 5 : 10) * p;
  const out = []; for (let v = Math.ceil(lo / step - 1e-9) * step; v <= hi + 1e-9 * span; v += step) out.push(Math.abs(v) < step * 1e-9 ? 0 : v);
  return out;
}

/** A panel with axes. box = {x, y, w, h} of the plotting area in mm. */
class Panel {
  constructor(box, xr, yr, o = {}) {
    this.b = box; this.xr = xr; this.yr = yr; this.o = o; this.items = [];
  }
  X(v) { return this.b.x + (v - this.xr[0]) / (this.xr[1] - this.xr[0]) * this.b.w; }
  Y(v) { return this.b.y + this.b.h - (v - this.yr[0]) / (this.yr[1] - this.yr[0]) * this.b.h; }
  /** Line through all points, decimated to a min/max envelope per bin. */
  line(t, v, o = {}) {
    const bins = o.bins || Math.round(this.b.w / 0.12), lo = this.xr[0], hi = this.xr[1];
    const mn = new Array(bins).fill(Infinity), mx = new Array(bins).fill(-Infinity), first = new Array(bins), last = new Array(bins);
    for (let i = 0; i < t.length; i++) {
      if (t[i] < lo || t[i] > hi) continue;
      let k = Math.floor((t[i] - lo) / (hi - lo) * bins); if (k >= bins) k = bins - 1;
      if (first[k] === undefined) first[k] = v[i];
      last[k] = v[i]; if (v[i] < mn[k]) mn[k] = v[i]; if (v[i] > mx[k]) mx[k] = v[i];
    }
    let d = '', started = false;
    for (let k = 0; k < bins; k++) {
      if (first[k] === undefined) continue;
      const x = this.b.x + (k + 0.5) / bins * this.b.w;
      const seq = [first[k], mn[k], mx[k], last[k]];
      for (const y of seq) { d += (started ? 'L' : 'M') + f(x) + ',' + f(this.Y(Math.max(this.yr[0], Math.min(this.yr[1], y)))); started = true; }
    }
    const dash = o.dash ? ` stroke-dasharray="${o.dash}"` : '';
    this.items.push(`<path d="${d}" fill="none" stroke="${o.stroke || BLACK}" stroke-width="${o.width || 0.25}" stroke-linejoin="round"${dash}/>`);
    return this;
  }
  /** Exact polyline (no decimation). */
  poly(t, v, o = {}) {
    let d = '';
    for (let i = 0; i < t.length; i++) d += (i ? 'L' : 'M') + f(this.X(t[i])) + ',' + f(this.Y(v[i]));
    const dash = o.dash ? ` stroke-dasharray="${o.dash}"` : '';
    this.items.push(`<path d="${d}" fill="none" stroke="${o.stroke || BLACK}" stroke-width="${o.width || 0.3}" stroke-linejoin="round"${dash}/>`);
    return this;
  }
  /** Spike raster: a short vertical tick per event. */
  raster(t, u, o = {}) {
    let d = ''; const len = o.len || 0.3;
    for (let i = 0; i < t.length; i++) {
      if (t[i] < this.xr[0] || t[i] > this.xr[1]) continue;
      d += 'M' + f(this.X(t[i])) + ',' + f(this.Y(u[i]) - len / 2) + 'v' + len;
    }
    this.items.push(`<path d="${d}" fill="none" stroke="${o.stroke || BLACK}" stroke-width="${o.width || 0.12}"/>`);
    return this;
  }
  hline(y, o = {}) {
    this.items.push(`<path d="M${f(this.b.x)},${f(this.Y(y))}H${f(this.b.x + this.b.w)}" stroke="${o.stroke || GREY}" stroke-width="${o.width || 0.15}" stroke-dasharray="${o.dash || '0.8 0.8'}" fill="none"/>`);
    return this;
  }
  label(xv, yv, s, o = {}) { this.items.push(text(this.X(xv), this.Y(yv), s, o)); return this; }
  raw(s) { this.items.push(s); return this; }
  render() {
    const b = this.b, o = this.o, out = [];
    out.push(`<g>`);
    out.push(`<clipPath id="c${o.id}"><rect x="${f(b.x)}" y="${f(b.y - 0.3)}" width="${f(b.w)}" height="${f(b.h + 0.6)}"/></clipPath>`);
    out.push(`<g clip-path="url(#c${o.id})">${this.items.filter(s => !s.startsWith('<text')).join('')}</g>`);
    out.push(this.items.filter(s => s.startsWith('<text')).join(''));
    // axes: left and bottom
    out.push(`<path d="M${f(b.x)},${f(b.y)}V${f(b.y + b.h)}H${f(b.x + b.w)}" fill="none" stroke="${BLACK}" stroke-width="${LW}"/>`);
    const xt = o.xticks || niceTicks(this.xr[0], this.xr[1], o.nx || 5), yt = o.yticks || niceTicks(this.yr[0], this.yr[1], o.ny || 4);
    for (const v of xt) {
      out.push(`<path d="M${f(this.X(v))},${f(b.y + b.h)}v1" stroke="${BLACK}" stroke-width="${LW}"/>`);
      if (o.xlabel !== null) out.push(text(this.X(v), b.y + b.h + 3.9, (o.xfmt || String)(v), { anchor: 'middle' }));
    }
    for (const v of yt) {
      out.push(`<path d="M${f(b.x)},${f(this.Y(v))}h-1" stroke="${BLACK}" stroke-width="${LW}"/>`);
      out.push(text(b.x - 1.6, this.Y(v) + 1, (o.yfmt || String)(v), { anchor: 'end' }));
    }
    if (o.xlabel) out.push(text(b.x + b.w / 2, b.y + b.h + 8, o.xlabel, { anchor: 'middle' }));
    if (o.ylabel) out.push(text(b.x - (o.yoff || 9.5), b.y + b.h / 2, o.ylabel, { anchor: 'middle', rotate: -90 }));
    if (o.tag) out.push(text(b.x - (o.yoff || 9.5) - 2.2, b.y - 1.2, o.tag, { bold: true, size: 3.5 }));
    out.push(`</g>`);
    return out.join('\n');
  }
}

/** Legend: entries = [{label, stroke, dash}] drawn at (x, y), one per line. */
function legend(x, y, entries) {
  return entries.map((e, i) => {
    const yy = y + i * 4;
    const dash = e.dash ? ` stroke-dasharray="${e.dash}"` : '';
    return `<path d="M${f(x)},${f(yy - 1)}h7" stroke="${e.stroke}" stroke-width="${e.width || 0.4}"${dash} fill="none"/>` + text(x + 8.5, yy, e.label);
  }).join('\n');
}

function box(x, y, w, h, o = {}) {
  const dash = o.dash ? ` stroke-dasharray="${o.dash}"` : '';
  return `<rect x="${f(x)}" y="${f(y)}" width="${f(w)}" height="${f(h)}" rx="${o.rx === undefined ? 1 : o.rx}" fill="${o.fill || '#FFFFFF'}" stroke="${o.stroke || BLACK}" stroke-width="${o.width || 0.25}"${dash}/>`;
}
/** Lines of text centred in a box, or left-aligned with o.left. */
function boxText(x, y, w, h, lines, o = {}) {
  const lh = o.lh || 3.5, n = lines.length, y0 = y + h / 2 - (n - 1) * lh / 2 + 1;
  return lines.map((l, i) => {
    const bold = (o.boldFirst && i === 0) || o.bold;
    return o.left ? text(x + 2, y0 + i * lh, l, { bold, size: o.size, italic: o.italic && i > 0 })
      : text(x + w / 2, y0 + i * lh, l, { anchor: 'middle', bold, size: o.size, italic: o.italic && i > 0 });
  }).join('');
}
function arrow(x1, y1, x2, y2, o = {}) {
  const dash = o.dash ? ` stroke-dasharray="${o.dash}"` : '';
  return `<path d="M${f(x1)},${f(y1)}L${f(x2)},${f(y2)}" stroke="${o.stroke || BLACK}" stroke-width="${o.width || 0.3}" fill="none" marker-end="url(#ah)"${dash}/>`;
}
function elbow(pts, o = {}) {
  const d = pts.map((p, i) => (i ? 'L' : 'M') + f(p[0]) + ',' + f(p[1])).join('');
  const dash = o.dash ? ` stroke-dasharray="${o.dash}"` : '';
  return `<path d="${d}" stroke="${o.stroke || BLACK}" stroke-width="${o.width || 0.3}" fill="none" marker-end="url(#ah)"${dash}/>`;
}
const DEFS = `<defs><marker id="ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse"><path d="M0,1L10,5L0,9z" fill="#000"/></marker></defs>`;

function svg(w, h, body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}mm" height="${h}mm" viewBox="0 0 ${w} ${h}">${DEFS}<rect width="${w}" height="${h}" fill="#FFFFFF"/>\n${body}\n</svg>`;
}
function html(w, h, svgText) {
  return `<!doctype html><html><head><meta charset="utf-8"><style>@page{size:${w}mm ${h}mm;margin:0}html,body{margin:0;padding:0;background:#fff}svg{display:block}</style></head><body>${svgText}</body></html>`;
}
module.exports = { Panel, text, legend, box, boxText, arrow, elbow, svg, html, niceTicks, FS, BLACK, BLUE, VERM, GREY, f };
