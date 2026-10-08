// Renders docs/dbdiagram.io/current_model.txt as an auto-laid-out ER diagram (SVG).
//
// ELK (layered, orthogonal routing, fixed ports on each column row) places the tables; the
// layout is run with several random seeds and the one with the fewest line crossings is kept.
// See README.md for usage.
import ELK from 'elkjs/lib/elk.bundled.js';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const args = process.argv.slice(2);
const opt = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? args[i + 1] : fallback;
};
const SRC = opt('in', path.join(repo, 'docs/dbdiagram.io/current_model.txt'));
const OUT = opt('out', path.join(repo, 'docs/dbdiagram.io/sportshub-model.svg'));
const SEEDS = Number(opt('seeds', '40'));

// Header colour per domain; tables not listed fall back to grey (and are reported).
const DOMAINS = [
  ['Rules', '#6d28d9', ['league_rule_set', 'game_plan_entry']],
  ['Federation & league structure', '#0f766e', ['federation', 'season', 'location', 'category', 'league', 'tier', 'comp_group']],
  ['Schedule & results', '#b91c1c', ['round', 'match_day', 'match_game', 'match_set', 'match_event']],
  ['Teams & rosters', '#b45309', ['club', 'team', 'team_participation', 'standing', 'roster_entry']],
  ['People & access', '#1d4ed8', ['player', 'club_membership', 'app_user', 'role_assignment']],
  ['Standalone (no relationships)', '#4b5563', ['release_note', 'entity_history', 'api_key', 'tracker_issue', 'tracker_issue_vote']],
];
const DOMAIN_COLOR = Object.fromEntries(DOMAINS.flatMap(([, c, ts]) => ts.map((t) => [t, c])));

const W = 250, HEAD = 34, ROW = 24, PAD_X = 40, PAD_Y = 60;

// ---------- parse DBML (the subset current_model.txt uses) ----------
const text = fs.readFileSync(SRC, 'utf8');
const tables = {};
for (const m of text.matchAll(/^Table (\w+) \{([\s\S]*?)^\}/gm)) {
  const cols = [];
  let inIndexes = false;
  for (let line of m[2].split('\n')) {
    line = line.split('//')[0].trim();
    if (line.startsWith('indexes')) { inIndexes = true; continue; }
    if (inIndexes) { if (line.startsWith('}')) inIndexes = false; continue; }
    if (!line || line.startsWith('Note')) continue;
    const c = line.match(/^(\w+)\s+([\w()]+)(?:\s*\[(.*)\])?/);
    if (c) cols.push({ name: c[1], type: c[2], pk: /\bpk\b/.test(c[3] ?? ''), notNull: /not null/.test(c[3] ?? '') });
  }
  tables[m[1]] = cols;
}
const refs = [...text.matchAll(/^Ref:\s*(\w+)\.(\w+)\s*([<>-])(\?)?\s*(\w+)\.(\w+)/gm)].map((m) => {
  const [a, b] = [{ t: m[1], c: m[2] }, { t: m[5], c: m[6] }];
  const [child, parent] = m[3] === '<' ? [b, a] : [a, b];
  return {
    child: child.t, col: child.c, parent: parent.t, pcol: parent.c,
    // cardinality at each end: the FK side is "many" unless it's a one-to-one (-);
    // the referenced side is exactly one for a required FK, zero-or-one for an optional (?) one
    childCard: m[3] === '-' ? '1' : '*',
    parentCard: m[4] ? '0..1' : '1',
  };
});

for (const r of refs) {
  for (const [t, c] of [[r.child, r.col], [r.parent, r.pcol]]) {
    if (!tables[t]?.some((x) => x.name === c)) throw new Error(`Ref points at unknown column ${t}.${c}`);
  }
}
const uncolored = Object.keys(tables).filter((t) => !DOMAIN_COLOR[t]);
if (uncolored.length) console.warn(`No domain colour for: ${uncolored.join(', ')} (add them to DOMAINS)`);

const height = (t) => HEAD + ROW * tables[t].length + 6;
const portKey = (r) => `${r.parent}.${r.pcol}`;
// Referenced columns hit by both required ("1") and optional ("0..1") refs.
const mixedPorts = [...new Set(refs.map(portKey))]
  .filter((k) => new Set(refs.filter((r) => portKey(r) === k).map((r) => r.parentCard)).size > 1);
const rowY = (t, c) => HEAD + ROW * tables[t].findIndex((x) => x.name === c) + ROW / 2;

// ---------- layout ----------

function graph(seed) {
  return {
    id: 'root',
    layoutOptions: {
      'elk.algorithm': 'layered', 'elk.direction': 'RIGHT', 'elk.edgeRouting': 'ORTHOGONAL',
      'elk.spacing.nodeNode': '50', 'elk.layered.spacing.nodeNodeBetweenLayers': '110',
      'elk.spacing.edgeNode': '24', 'elk.spacing.edgeEdge': '14', 'elk.layered.spacing.edgeEdgeBetweenLayers': '14',
      'elk.layered.crossingMinimization.strategy': 'LAYER_SWEEP', 'elk.layered.thoroughness': '200',
      'elk.layered.nodePlacement.strategy': 'NETWORK_SIMPLEX', 'elk.spacing.componentComponent': '90',
      'elk.randomSeed': String(seed),
    },
    children: Object.keys(tables).map((t) => ({
      id: t, width: W, height: height(t),
      layoutOptions: { 'elk.portConstraints': 'FIXED_POS' },
      // parent side: the referenced column on the right edge; child side: each FK on the left edge
      ports: [
        ...[...new Set(refs.filter((r) => r.parent === t).map((r) => r.pcol))]
          .map((c) => ({ id: `${t}.${c}>`, x: W, y: rowY(t, c), width: 0, height: 0, layoutOptions: { 'elk.port.side': 'EAST' } })),
        ...refs.filter((r) => r.child === t)
          .map((r) => ({ id: `${t}.${r.col}<`, x: 0, y: rowY(t, r.col), width: 0, height: 0, layoutOptions: { 'elk.port.side': 'WEST' } })),
      ],
    })),
    edges: refs.map((r, i) => ({ id: `e${i}`, sources: [`${portKey(r)}>`], targets: [`${r.child}.${r.col}<`] })),
  };
}

// Each edge as a polyline, child ("many") end first.
function polylines(res) {
  return res.edges.flatMap((e, i) => e.sections.map((s) => ({
    ref: refs[i],
    pts: [s.startPoint, ...(s.bendPoints ?? []), s.endPoint].map((p) => [p.x + PAD_X, p.y + PAD_Y]).reverse(),
  })));
}

function crossings(lines) {
  const segs = (pts) => pts.slice(1).map((p, i) => [pts[i], p]);
  const cross = (a, b) => {
    const av = a[0][0] === a[1][0], bv = b[0][0] === b[1][0];
    if (av === bv) return false;
    const [v, h] = av ? [a, b] : [b, a];
    const [vy1, vy2] = [v[0][1], v[1][1]].sort((p, q) => p - q);
    const [hx1, hx2] = [h[0][0], h[1][0]].sort((p, q) => p - q);
    return hx1 < v[0][0] && v[0][0] < hx2 && vy1 < h[0][1] && h[0][1] < vy2;
  };
  let n = 0;
  for (let i = 0; i < lines.length; i++)
    for (let j = i + 1; j < lines.length; j++)
      for (const s1 of segs(lines[i].pts)) for (const s2 of segs(lines[j].pts)) if (cross(s1, s2)) n++;
  return n;
}

const elk = new ELK();
let best;
for (let seed = 1; seed <= SEEDS; seed++) {
  const res = await elk.layout(graph(seed));
  const lines = polylines(res);
  const n = crossings(lines);
  if (!best || n < best.n) best = { seed, n, res, lines };
}

// At a mixed column, the required ("1") lines are drawn 6px parallel to their routed path, end to
// end, so they never share a stretch with the optional ("0..1") lines and each cardinality gets
// its own line and label. Both ends stay inside their table rows; per column, the side (left or
// right of the path) that gives the fewest crossings wins.
const NUDGE = 6;
function offsetPolyline(pts, d) {
  const normal = (a, b) => [-Math.sign(b[1] - a[1]), Math.sign(b[0] - a[0])]; // left of travel
  return pts.map((p, i) => {
    const n1 = i > 0 ? normal(pts[i - 1], p) : null;
    const n2 = i < pts.length - 1 ? normal(p, pts[i + 1]) : null;
    const n = !n1 ? n2 : !n2 ? n1 : n1[0] === n2[0] && n1[1] === n2[1] ? n1 : [n1[0] + n2[0], n1[1] + n2[1]];
    return [p[0] + n[0] * d, p[1] + n[1] * d];
  });
}
function nudged(lines, signs) {
  return lines.map((l) => {
    const sign = l.ref.parentCard === '1' ? signs[portKey(l.ref)] : undefined;
    if (!sign) return l;
    return { ...l, pts: offsetPolyline(l.pts, sign * NUDGE) };
  });
}
let bestSigns = {}, bestLines = nudged(best.lines, {}), bestN = Infinity;
for (let mask = 0; mask < 2 ** mixedPorts.length; mask++) {
  const signs = Object.fromEntries(mixedPorts.map((k, i) => [k, mask & (1 << i) ? 1 : -1]));
  const lines = nudged(best.lines, signs);
  const n = crossings(lines);
  if (n < bestN) [bestSigns, bestLines, bestN] = [signs, lines, n];
}
best.lines = bestLines;
console.log(`${Object.keys(tables).length} tables, ${refs.length} refs — best seed ${best.seed}: ${bestN} crossings`);

// ---------- SVG ----------
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const pos = Object.fromEntries(best.res.children.map((n) => [n.id, [n.x + PAD_X, n.y + PAD_Y]]));
const maxX = Math.max(...Object.values(pos).map(([x]) => x + W)) + 80;
const maxY = Math.max(...Object.entries(pos).map(([t, [, y]]) => y + height(t))) + 60;
const LINE = '#6b7280';
const out = [
  `<svg xmlns="http://www.w3.org/2000/svg" width="${maxX}" height="${maxY}" viewBox="0 0 ${maxX} ${maxY}" font-family="Inter, Segoe UI, Helvetica, Arial, sans-serif">`,
  '<rect width="100%" height="100%" fill="#f7f7f8"/>',
];
for (const { pts } of best.lines) {
  out.push(`<path d="M${pts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' L')}" fill="none" stroke="${LINE}" stroke-width="1.6" stroke-linejoin="round"/>`);
  // crow's foot at the child end ("many"), bar at the parent end ("one")
  const [[x0, y0], [x1]] = pts;
  const dc = x1 > x0 ? 1 : -1;
  out.push(`<path d="M${x0},${y0 - 6} L${x0 + 10 * dc},${y0} L${x0},${y0 + 6}" fill="none" stroke="${LINE}" stroke-width="1.6"/>`);
  const [[xa], [xb, yb]] = pts.slice(-2);
  const de = xa > xb ? 1 : -1;
  out.push(`<path d="M${xb + 8 * de},${yb - 6} L${xb + 8 * de},${yb + 6}" stroke="${LINE}" stroke-width="1.6"/>`);
}
// Cardinality labels: "*" beside each crow's foot, and one "1" / "0..1" per cardinality at each
// referenced column. Each label tries both sides of its line at growing distances from the table
// and takes the first spot that touches no line, table, end marker or earlier label. At a mixed
// column the nudged "1" line's label prefers its outer side, the "0..1" label the opposite one.
const tableBoxes = Object.entries(pos).map(([t, [x, y]]) => [x, y, x + W, y + height(t)]);
const lineBoxes = best.lines.flatMap(({ pts }) => pts.slice(1).map((q, i) => {
  const p = pts[i];
  return [Math.min(p[0], q[0]) - 1.5, Math.min(p[1], q[1]) - 1.5, Math.max(p[0], q[0]) + 1.5, Math.max(p[1], q[1]) + 1.5];
}));
const markerBoxes = best.lines.flatMap(({ pts }) => [pts[0], pts.at(-1)].map(([x, y]) => [x - 12, y - 7, x + 12, y + 7]));
const placed = [], unplaced = [];
const overlaps = (a, b) => a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
function placeLabel([x0, y0], [x1], text, preferBelow) {
  const big = text === '*';
  const w = big ? 8 : text.length * 6.6;
  const [top, bottom] = big ? [-11, -5] : [-9, 0]; // glyph extent relative to the baseline
  const dir = x1 > x0 ? 1 : -1;
  const sides = preferBelow ? ['below', 'above'] : ['above', 'below'];
  let fallback;
  for (const gap of [14, 22, 30, 40, 52, 66, 82]) {
    for (const side of sides) {
      const left = dir > 0 ? x0 + gap : x0 - gap - w;
      const baseline = side === 'above' ? y0 - 3 - bottom : y0 + 3 - top;
      const box = [left - 1, baseline + top - 1, left + w + 1, baseline + bottom + 1];
      const hits = [...lineBoxes, ...tableBoxes, ...markerBoxes, ...placed].filter((b) => overlaps(box, b)).length;
      if (!fallback || hits < fallback.hits) fallback = { hits, left, baseline, box };
      if (!hits) break;
    }
    if (fallback.hits === 0) break;
  }
  if (fallback.hits) unplaced.push(text);
  placed.push(fallback.box);
  return `<text x="${fallback.left.toFixed(1)}" y="${fallback.baseline.toFixed(1)}" font-size="${big ? 15 : 11}" font-weight="700" fill="#374151">${esc(text)}</text>`;
}
const drawnPorts = new Set();
for (const line of best.lines) {
  const id = `${portKey(line.ref)}>${line.ref.parentCard}`;
  if (drawnPorts.has(id)) continue;
  drawnPorts.add(id);
  // at a mixed column, find where the "1" line ended up (above or below the row centre)
  const req = mixedPorts.includes(portKey(line.ref))
    ? best.lines.find((l) => portKey(l.ref) === portKey(line.ref) && l.ref.parentCard === '1') : null;
  const reqBelow = req ? req.pts.at(-1)[1] > pos[req.ref.parent][1] + rowY(req.ref.parent, req.ref.pcol) : false;
  const below = req ? (line.ref.parentCard === '1' ? reqBelow : !reqBelow) : false;
  out.push(placeLabel(line.pts.at(-1), line.pts.at(-2), line.ref.parentCard, below));
}
for (const line of best.lines) out.push(placeLabel(line.pts[0], line.pts[1], line.ref.childCard, false));
if (unplaced.length) console.warn(`${unplaced.length} cardinality label(s) found no free spot and overlap something`);
for (const [t, [x, y]] of Object.entries(pos)) {
  const fks = new Set(refs.filter((r) => r.child === t).map((r) => r.col));
  out.push(`<g><rect x="${x}" y="${y}" width="${W}" height="${height(t)}" rx="8" fill="#ffffff" stroke="#d1d5db"/>`);
  out.push(`<path d="M${x},${y + 8} a8,8 0 0 1 8,-8 h${W - 16} a8,8 0 0 1 8,8 v${HEAD - 8} h-${W} z" fill="${DOMAIN_COLOR[t] ?? '#374151'}"/>`);
  out.push(`<text x="${x + 12}" y="${y + 22}" font-size="14" font-weight="700" fill="#ffffff">${esc(t)}</text>`);
  tables[t].forEach((c, i) => {
    const ry = y + HEAD + ROW * i;
    if (i % 2) out.push(`<rect x="${x + 1}" y="${ry}" width="${W - 2}" height="${ROW}" fill="#f3f4f6"/>`);
    const mark = c.pk ? ' 🔑' : fks.has(c.name) ? ' ↗' : '';
    out.push(`<text x="${x + 12}" y="${ry + 16}" font-size="12.5" font-weight="${c.pk ? 700 : 400}" fill="#111827">${esc(c.name)}${mark}</text>`);
    out.push(`<text x="${x + W - 12}" y="${ry + 16}" font-size="11.5" text-anchor="end" fill="#6b7280">${esc(c.type)}${c.notNull && !c.pk ? ' !' : ''}</text>`);
  });
  out.push('</g>');
}
const lx = maxX - 330, ly = maxY - 98 - 26 * DOMAINS.length;
out.push(`<rect x="${lx - 16}" y="${ly - 30}" width="310" height="${26 * DOMAINS.length + 92}" rx="10" fill="#ffffff" stroke="#d1d5db"/>`);
out.push(`<text x="${lx}" y="${ly - 8}" font-size="14" font-weight="700" fill="#111827">Sportshub data model</text>`);
DOMAINS.forEach(([label, color], i) => {
  out.push(`<rect x="${lx}" y="${ly + 8 + 26 * i}" width="16" height="16" rx="3" fill="${color}"/>`);
  out.push(`<text x="${lx + 26}" y="${ly + 21 + 26 * i}" font-size="12.5" fill="#111827">${esc(label)}</text>`);
});
out.push(`<text x="${lx}" y="${ly + 26 * DOMAINS.length + 30}" font-size="11.5" fill="#6b7280">🔑 primary key · ↗ foreign key · ! not null</text>`);
out.push(`<text x="${lx}" y="${ly + 26 * DOMAINS.length + 48}" font-size="11.5" fill="#6b7280">* many · 1 exactly one · 0..1 optional</text>`);
out.push('</svg>');
fs.writeFileSync(OUT, out.join('\n'));
console.log(`wrote ${path.relative(process.cwd(), OUT)}`);
