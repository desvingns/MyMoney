// neon/neon-core.jsx — тёмно-неоновая система: цвет/свечение, графики, общие блоки
// Зависит от concepts/shared.jsx (CCATS, CCIcon, ccFmt, ccUseGrow и т.д.)
// Экспортирует в window: catColor, accentOf, neonGlow, withA, lighten, NeonChart,
//   NeonHeader, NeonFabs, MiniBar, NeonTx, NEON_TX, CAT_BY_KEY

// ── цветовые помощники ────────────────────────────────────────
function toRGB(c) {
  if (c[0] === '#') {
    const h = c.slice(1); const s = h.length === 3 ? h.replace(/./g, x => x + x) : h;
    return [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16)];
  }
  const m = c.match(/-?\d+\.?\d*/g); return m ? m.slice(0, 3).map(Number) : [255, 255, 255];
}
function withA(c, a) { const [r, g, b] = toRGB(c); return `rgba(${r},${g},${b},${a})`; }
function lighten(c, f) {
  const [r, g, b] = toRGB(c);
  const ch = v => Math.round(f <= 1 ? v * f : v + (255 - v) * (f - 1));
  return `rgb(${ch(r)},${ch(g)},${ch(b)})`;
}

// неоновая палитра по категориям (мульти-режим)
const NEON = {
  food: '#FF5DA2', housing: '#39C0FF', cafe: '#42E6A4', transport: '#FF6B6B',
  entertainment: '#FF9F45', clothing: '#C77DFF', health: '#FF4D7D', pets: '#26E0C8',
  phone: '#7CA8FF', sport: '#8BF5A0', gifts: '#FF8FB8', bills: '#FFCE4D',
};
const HUE_BASE = { multi: '#37E1C0', cyan: '#33E1FF', lime: '#A8FF5C', magenta: '#FF5DC8', violet: '#9E86FF', amber: '#FFC24A' };

// цвет категории с учётом режима неона
function catColor(cat, idx, t) {
  if (!t || t.neonHue === 'multi') return NEON[cat.key] || lighten(cat.color, 1.3);
  const base = HUE_BASE[t.neonHue] || '#33E1FF';
  const f = 1.18 - Math.min(idx, 7) * 0.085;
  return lighten(base, f);
}
const accentOf = (t) => (!t || t.neonHue === 'multi') ? '#37E1C0' : HUE_BASE[t.neonHue];

// свечение, масштабируемое интенсивностью (0..100)
function neonGlow(color, t, mult = 1) {
  const k = (t ? t.neonIntensity : 50) / 100;
  if (k <= 0) return '0 0 0 transparent';
  const a = Math.min(0.95, 0.22 + k * 0.6);
  const blur = (7 + k * 24) * mult;
  return `0 0 ${blur}px ${withA(color, a)}`;
}
const textGlow = (color, t, mult = 1) => {
  const k = (t ? t.neonIntensity : 50) / 100;
  if (k <= 0) return 'none';
  return `0 0 ${(6 + k * 16) * mult}px ${withA(color, Math.min(0.9, 0.3 + k * 0.5))}`;
};

const CAT_BY_KEY = Object.fromEntries(CCATS.map(c => [c.key, c]));

// ── примеры операций ──────────────────────────────────────────
const NEON_TX = [
  { key: 'food', title: 'Пятёрочка', when: 'Сегодня · 14:20', amount: -1240 },
  { key: 'cafe', title: 'Кофемания', when: 'Сегодня · 09:05', amount: -560 },
  { key: 'income', title: 'Зарплата', when: 'Вчера · 11:00', amount: 85000 },
  { key: 'transport', title: 'Метро', when: 'Вчера · 18:42', amount: -150 },
  { key: 'entertainment', title: 'КАРО Фильм', when: '11 апр · 20:10', amount: -900 },
  { key: 'housing', title: 'Аренда', when: '5 апр · 10:00', amount: -32000 },
];

// ══════════════════════════════════════════════════════════════
//  ГРАФИК — одна точка входа, 4 формы (Tweaks: тип графика)
// ══════════════════════════════════════════════════════════════
function NeonChart({ data, t, w = 350, h = 200, compact = false }) {
  const type = (t && t.graphType) || 'bars';
  if (type === 'bubbles') return <ChartBubbles data={data} t={t} w={w} h={h} compact={compact} />;
  if (type === 'ring') return <ChartRing data={data} t={t} w={w} h={h} compact={compact} />;
  if (type === 'wave') return <ChartWave data={data} t={t} w={w} h={h} compact={compact} />;
  return <ChartBars data={data} t={t} w={w} h={h} compact={compact} />;
}

// ── Столбцы ───────────────────────────────────────────────────
function ChartBars({ data, t, w, h, compact }) {
  const items = data.cats.slice(0, compact ? 6 : 8);
  const max = Math.max(...items.map(c => c.amount));
  const grow = ccUseGrow(1, 'bars' + (t && t.neonHue));
  const labels = !compact;
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: compact ? 6 : 9, height: h, width: w }}>
      {items.map((c, i) => {
        const col = catColor(c, i, t);
        const bh = Math.max(8, (c.amount / max) * (h - (labels ? 26 : 8)) * grow);
        return (
          <div key={c.key} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 7, height: '100%', justifyContent: 'flex-end' }}>
            <div style={{
              width: '100%', height: bh, borderRadius: '6px 6px 3px 3px',
              background: `linear-gradient(180deg, ${col} 0%, ${withA(col, 0.14)} 100%)`,
              boxShadow: `${neonGlow(col, t, 0.85)}, inset 0 1px 0 ${withA('#fff', 0.45)}`,
              position: 'relative',
            }}>
              <div style={{ position: 'absolute', top: -1.5, left: 0, right: 0, height: 3, borderRadius: 3, background: col, boxShadow: neonGlow(col, t, 1.3) }}></div>
            </div>
            {labels && <CCIcon name={c.icon} size={13} color={withA(col, 0.9)} />}
          </div>
        );
      })}
    </div>
  );
}

// ── Пузыри (упаковка кругов) ──────────────────────────────────
function packBubbles(items, w, h) {
  const maxA = Math.max(...items.map(c => c.amount));
  const rMax = Math.min(w, h) * 0.27, rMin = Math.min(w, h) * 0.07;
  const nodes = items.map(c => ({ c, r: rMin + Math.sqrt(c.amount / maxA) * (rMax - rMin), x: null, y: null }));
  const placed = [];
  nodes.forEach((nd, idx) => {
    if (idx === 0) { nd.x = w / 2; nd.y = h / 2; placed.push(nd); return; }
    let ang = idx * 2.39996, rad = (placed[0].r + nd.r) * 0.6;
    for (let k = 0; k < 4000; k++) {
      const x = w / 2 + Math.cos(ang) * rad, y = h / 2 + Math.sin(ang) * rad;
      const ok = x - nd.r >= 0 && x + nd.r <= w && y - nd.r >= 0 && y + nd.r <= h &&
        placed.every(p => Math.hypot(p.x - x, p.y - y) >= p.r + nd.r + 5);
      if (ok) { nd.x = x; nd.y = y; break; }
      ang += 0.45; rad += 0.55;
    }
    if (nd.x == null) { nd.x = w / 2; nd.y = h / 2; }
    placed.push(nd);
  });
  return nodes;
}
function ChartBubbles({ data, t, w, h, compact }) {
  const items = data.cats.slice(0, compact ? 6 : 9);
  const grow = ccUseGrow(1, 'bub' + (t && t.neonHue));
  const nodes = React.useMemo(() => packBubbles(items, w, h), [w, h, data.label, items.length]);
  return (
    <div style={{ position: 'relative', width: w, height: h }}>
      {nodes.map(({ c, x, y, r }, i) => {
        const col = catColor(c, i, t); const rr = r * grow;
        return (
          <div key={c.key} title={c.label} style={{
            position: 'absolute', left: x - rr, top: y - rr, width: rr * 2, height: rr * 2, borderRadius: '50%',
            background: `radial-gradient(circle at 38% 32%, ${withA(col, 0.42)}, ${withA(col, 0.08)} 72%)`,
            border: `1.5px solid ${withA(col, 0.9)}`, boxShadow: `${neonGlow(col, t, 0.9)}, inset 0 0 ${rr * 0.6}px ${withA(col, 0.25)}`,
            display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 1,
          }}>
            {rr >= 18 && <CCIcon name={c.icon} size={Math.min(rr * 0.5, 20)} color={lighten(col, 1.4)} />}
            {rr >= 24 && <span style={{ fontSize: 10, fontWeight: 800, color: '#fff', textShadow: textGlow(col, t) }}>{Math.round(c.pct * 100)}%</span>}
          </div>
        );
      })}
    </div>
  );
}

// ── Кольца (концентрические дуги, не пончик) ──────────────────
function ChartRing({ data, t, w, h, compact }) {
  const items = data.cats.slice(0, compact ? 5 : 6);
  const grow = ccUseGrow(1, 'ring' + (t && t.neonHue));
  const size = Math.min(w, h); const cx = w / 2, cy = h / 2;
  const sw = compact ? 6 : 8; const gap = compact ? 12 : 15;
  const r0 = size / 2 - sw;
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} style={{ overflow: 'visible' }}>
      <defs>
        {items.map((c, i) => {
          const col = catColor(c, i, t);
          return <filter key={i} id={`ng${i}`} x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation={((t ? t.neonIntensity : 50) / 100) * 3.5} result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>;
        })}
      </defs>
      {items.map((c, i) => {
        const col = catColor(c, i, t); const r = r0 - i * gap;
        const circ = 2 * Math.PI * r; const len = circ * c.pct * grow;
        return (
          <g key={c.key} transform={`rotate(-90 ${cx} ${cy})`}>
            <circle cx={cx} cy={cy} r={r} fill="none" stroke={withA(col, 0.12)} strokeWidth={sw} />
            <circle cx={cx} cy={cy} r={r} fill="none" stroke={col} strokeWidth={sw} strokeLinecap="round"
              strokeDasharray={`${len} ${circ}`} filter={`url(#ng${i})`} />
          </g>
        );
      })}
      <text x={cx} y={cy - 4} textAnchor="middle" fontSize="11" fontWeight="700" fill={withA('#fff', 0.5)} letterSpacing="1">ТРАТЫ</text>
      <text x={cx} y={cy + 14} textAnchor="middle" fontSize="15" fontWeight="800" fill="#fff">{ccFmtPlain(data.expense)}</text>
    </svg>
  );
}

// ── Волна (тренд за 7 дней, неоновая заливка) ─────────────────
function ChartWave({ data, t, w, h, compact }) {
  const acc = accentOf(t);
  const grow = ccUseGrow(1, 'wave' + (t && t.neonHue));
  const pts = React.useMemo(() => {
    const n = 7; const arr = [];
    for (let i = 0; i < n; i++) {
      const s = Math.sin((i + 1) * 12.9898 + data.label.length * 7.7) * 43758.5;
      arr.push(0.35 + (s - Math.floor(s)) * 0.6);
    }
    return arr;
  }, [data.label]);
  const pad = 6; const iw = w - pad * 2, ih = h - 20;
  const max = Math.max(...pts);
  const xy = pts.map((p, i) => [pad + (i / (pts.length - 1)) * iw, 14 + ih - (p / max) * ih * grow]);
  const line = xy.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' ');
  const area = `${line} L${(pad + iw).toFixed(1)} ${14 + ih} L${pad} ${14 + ih} Z`;
  const days = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} style={{ overflow: 'visible' }}>
      <defs>
        <linearGradient id="wv" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={withA(acc, 0.4)} /><stop offset="1" stopColor={withA(acc, 0)} /></linearGradient>
        <filter id="wvg" x="-20%" y="-50%" width="140%" height="200%"><feGaussianBlur stdDeviation={((t ? t.neonIntensity : 50) / 100) * 3} result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
      </defs>
      <path d={area} fill="url(#wv)" />
      <path d={line} fill="none" stroke={acc} strokeWidth="2.4" strokeLinejoin="round" strokeLinecap="round" filter="url(#wvg)" />
      {xy.map((p, i) => <circle key={i} cx={p[0]} cy={p[1]} r={i === xy.length - 1 ? 4 : 2.6} fill={i === xy.length - 1 ? lighten(acc, 1.4) : acc} filter="url(#wvg)" />)}
      {!compact && xy.map((p, i) => <text key={'d' + i} x={p[0]} y={h - 2} textAnchor="middle" fontSize="9" fill={withA('#fff', 0.35)}>{days[i]}</text>)}
    </svg>
  );
}

// ══════════════════════════════════════════════════════════════
//  ОБЩИЕ БЛОКИ
// ══════════════════════════════════════════════════════════════

// шапка: логотип + период (месяц/год/диапазон) с навигацией
function NeonHeader({ data, t, offset, setOffset, mode, setMode, accent, dim = '#7B8499', mono }) {
  const acc = accent || accentOf(t);
  const chip = (id, label) => (
    <button key={id} onClick={() => setMode(id)} style={{
      appearance: 'none', border: 'none', cursor: 'pointer', padding: '4px 10px', borderRadius: 8,
      font: 'inherit', fontSize: 11, fontWeight: 700, letterSpacing: 0.3,
      background: mode === id ? withA(acc, 0.16) : 'transparent',
      color: mode === id ? lighten(acc, 1.3) : dim,
      boxShadow: mode === id ? `inset 0 0 0 1px ${withA(acc, 0.5)}` : 'none',
    }}>{label}</button>
  );
  const arrow = (icon, d) => (
    <button onClick={() => setOffset(o => o + d)} style={{ appearance: 'none', border: 'none', background: 'transparent', cursor: 'pointer', color: dim, width: 30, height: 30, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <CCIcon name={icon} size={20} />
    </button>
  );
  const rangeLabel = mode === 'range' ? '1 – 30 ' + data.label.split(' ')[0].toLowerCase()
    : mode === 'year' ? data.label.split(' ')[1] : data.label;
  return (
    <div style={{ padding: '6px 20px 10px', flexShrink: 0 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
        <span style={{ fontSize: 21, fontWeight: 800, letterSpacing: -0.5, color: '#fff', fontFamily: mono ? "'Space Mono', monospace" : undefined }}>
          My<span style={{ color: acc, textShadow: textGlow(acc, t) }}>Money</span>
        </span>
        <div style={{ display: 'flex', gap: 4, padding: 3, borderRadius: 11, background: withA('#fff', 0.04), boxShadow: `inset 0 0 0 1px ${withA('#fff', 0.06)}` }}>
          {chip('month', 'Месяц')}{chip('year', 'Год')}{chip('range', 'Период')}
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        {arrow('chevL', -1)}
        <span style={{ fontSize: 15, fontWeight: 700, color: '#fff', letterSpacing: 0.2, textTransform: 'capitalize' }}>{rangeLabel}</span>
        {arrow('chevR', 1)}
      </div>
    </div>
  );
}

// мини-полоска доли
function MiniBar({ frac, color, t, h = 5, track = withA('#fff', 0.08) }) {
  const grow = ccUseGrow(1, 'mb' + (t && t.neonHue));
  return (
    <div style={{ height: h, borderRadius: h, background: track, overflow: 'hidden' }}>
      <div style={{ height: '100%', width: `${Math.max(3, frac * 100 * grow)}%`, borderRadius: h, background: color, boxShadow: neonGlow(color, t, 0.5) }}></div>
    </div>
  );
}

// большие круглые кнопки − / +
function NeonFabs({ t, size = 60, gap = 70, style }) {
  const red = '#FF5D6E', green = '#3DF59B';
  const fab = (col, icon) => (
    <button style={{
      appearance: 'none', border: `1.5px solid ${withA(col, 0.7)}`, cursor: 'pointer',
      width: size, height: size, borderRadius: size / 2,
      background: `radial-gradient(circle at 50% 35%, ${withA(col, 0.3)}, ${withA(col, 0.06)})`,
      color: lighten(col, 1.4), display: 'flex', alignItems: 'center', justifyContent: 'center',
      boxShadow: `${neonGlow(col, t, 1)}, inset 0 0 14px ${withA(col, 0.2)}`,
    }}><CCIcon name={icon} size={size * 0.42} color={lighten(col, 1.4)} /></button>
  );
  return (
    <div style={{ display: 'flex', justifyContent: 'center', gap, padding: '8px 0 14px', flexShrink: 0, ...style }}>
      {fab(red, 'remove')}{fab(green, 'add')}
    </div>
  );
}

// строка операции
function NeonTx({ tx, t, last }) {
  const cat = tx.key === 'income' ? { color: '#3DF59B', icon: 'wallet', label: 'Доход' } : CAT_BY_KEY[tx.key];
  const inc = tx.amount > 0;
  const col = inc ? '#3DF59B' : catColor(cat, 0, t);
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '9px 0', borderBottom: last ? 'none' : `1px solid ${withA('#fff', 0.05)}` }}>
      <div style={{ width: 34, height: 34, borderRadius: 11, flexShrink: 0, background: withA(col, 0.12), display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: `inset 0 0 0 1px ${withA(col, 0.3)}` }}>
        <CCIcon name={cat.icon} size={17} color={col} />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 14, fontWeight: 700, color: '#EAF0FA', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{tx.title}</div>
        <div style={{ fontSize: 11, color: '#6B7488' }}>{tx.when}</div>
      </div>
      <span style={{ fontSize: 14, fontWeight: 800, color: inc ? '#3DF59B' : '#EAF0FA', textShadow: inc ? textGlow('#3DF59B', t, 0.7) : 'none', fontVariantNumeric: 'tabular-nums' }}>
        {inc ? '+' : '−'}{ccFmtPlain(Math.abs(tx.amount))}
      </span>
    </div>
  );
}

Object.assign(window, {
  toRGB, withA, lighten, catColor, accentOf, neonGlow, textGlow,
  NEON, HUE_BASE, CAT_BY_KEY, NEON_TX,
  NeonChart, ChartBars, ChartBubbles, ChartRing, ChartWave,
  NeonHeader, MiniBar, NeonFabs, NeonTx,
});
