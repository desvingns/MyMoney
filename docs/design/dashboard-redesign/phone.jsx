// phone.jsx — faithful recreation of the budget dashboard window (branded MyMoney).
// Beveled donut, leader-line category icons + percentages, income/expense centre,
// balance pill and the two large +/- FABs. Driven entirely by the `cfg` prop.
// Exports to window: Phone

const LOGO_FONTS = {
  script: "'Pacifico', cursive",
  rounded: "'Baloo 2', system-ui, sans-serif",
  clean: "'Nunito', system-ui, sans-serif"
};

// ── geometry helpers ────────────────────────────────────────
function polar(cx, cy, r, deg) {
  const a = (deg - 90) * Math.PI / 180;
  return [cx + r * Math.cos(a), cy + r * Math.sin(a)];
}
function arcPath(cx, cy, r, start, end) {
  const [sx, sy] = polar(cx, cy, r, start);
  const [ex, ey] = polar(cx, cy, r, end);
  const large = end - start <= 180 ? 0 : 1;
  return `M ${sx} ${sy} A ${r} ${r} 0 ${large} 1 ${ex} ${ey}`;
}
// rectangular icon frame, asymmetric vertically: top edge sits at -hhTop,
// bottom edge at +hhBot, so the top icon row can ride higher than the bottom.
function framePoint(t, hw, hhTop, hhBot) {
  const side = hhTop + hhBot;
  const P = 4 * hw + 2 * side;
  let d = (t % 1 + 1) % 1 * P;
  if (d < hw) return [d, -hhTop]; // top-centre → right
  d -= hw;
  if (d < side) return [hw, -hhTop + d]; // right edge, down
  d -= side;
  if (d < 2 * hw) return [hw - d, hhBot]; // bottom edge, right → left
  d -= 2 * hw;
  if (d < side) return [-hw, hhBot - d]; // left edge, up
  d -= side;
  return [-hw + d, -hhTop]; // top edge, left → centre
}

// ── money formatting "2 442 740,80 ₽" with small kopecks ─────
function Money({ value, symbol, color, size, kopecks = true }) {
  const neg = value < 0;
  const abs = Math.abs(value);
  const int = Math.floor(abs);
  const frac = Math.round((abs - int) * 100).toString().padStart(2, '0');
  const intStr = int.toLocaleString('ru-RU').replace(/\u00A0|,/g, ' ');
  return (
    <span style={{ ...{ color, fontSize: size, whiteSpace: 'nowrap', padding: "0px 0px 0px 11px", fontWeight: "400", fontFamily: "\"Times New Roman\"", letterSpacing: "-0.2px" }, padding: "0px", fontSize: "25px" }}>
      {neg ? '−' : ''}{intStr}{kopecks && <span style={{ fontSize: '0.64em', fontWeight: 800 }}>,{frac}</span>}
      <span style={{ fontSize: '0.82em' }}> {symbol}</span>
    </span>);

}

// ── colour helper ────────────────────────────────────────────
function shadeHex(hex, f) {
  let h = (hex || '#888888').replace('#', '');
  if (h.length === 3) h = h.split('').map((x) => x + x).join('');
  const n = parseInt(h, 16);
  const r = n >> 16 & 255,g = n >> 8 & 255,b = n & 255;
  const t = f < 0 ? 0 : 255,p = Math.abs(f);
  const m = (v) => Math.round((t - v) * p + v);
  return `rgb(${m(r)},${m(g)},${m(b)})`;
}

// ── the donut ────────────────────────────────────────────────
// `cfg.donut3D` selects the cross-section treatment:
//   flat · bevel · gloss(glass) · extrude(real depth) · soft(neo) · inset(emboss)
// Each look is faked with concentric stroked bands; `extrude` stacks darkened
// arcs downward to build a true side-wall.
function Donut({ cfg, cx, cy }) {
  const cats = cfg.categories;
  const total = cats.reduce((s, c) => s + (c.pct || 0), 0) || 1;
  const D = cfg.donutSize,th = cfg.thickness,r = (D - th) / 2;
  let cursor = 0;
  const arcs = cats.map((c) => {
    const sweep = c.pct / total * 360;
    const g = Math.min(cfg.gap, sweep * 0.6);
    const a = { ...c, start: cursor + g / 2, end: cursor + sweep - g / 2, mid: cursor + sweep / 2 };
    cursor += sweep;
    return a;
  });
  const cap = cfg.roundCaps ? 'round' : 'butt';
  const raw = cfg.donut3D;
  const mode = raw === true ? 'bevel' : raw === false ? 'flat' : raw || 'bevel';
  const uid = cfg._uid || 'main';
  const glossId = uid + '-upper';

  // band(radius, color, width, key, dy?, clip?) → array of arc paths
  const band = (rr, sc, sw, key, dy, clip) => arcs.map((a, i) => a.end > a.start &&
  <path key={key + i} d={arcPath(cx, cy + (dy || 0), rr, a.start, a.end)} fill="none"
  stroke={sc} strokeWidth={sw} strokeLinecap={cap} clipPath={clip} />
  );

  const baseArcs = arcs.map((a, i) => a.end > a.start &&
  <path key={'b' + i} d={arcPath(cx, cy, r, a.start, a.end)} fill="none"
  stroke={a.color} strokeWidth={th} strokeLinecap={cap} />
  );

  const float = mode === 'bevel' || mode === 'gloss' || mode === 'soft' || mode === 'extrude';
  const castShadow = float &&
  <g style={{ filter: 'blur(7px)' }}>
      <circle cx={cx} cy={cy + (mode === 'extrude' ? th * 0.95 : th * 0.5)} r={r}
    fill="none" stroke="rgba(35,60,48,0.28)" strokeWidth={th * 0.92} />
    </g>;

  // real extruded side wall: stacked darkened arcs offset downward
  let walls = null,extrudeTop = null;
  if (mode === 'extrude') {
    const depth = Math.min(22, Math.max(7, Math.round(th * 0.62)));
    const layers = [];
    for (let k = depth; k >= 1; k--) {
      arcs.forEach((a, i) => {
        if (a.end > a.start) layers.push(
          <path key={'w' + k + '_' + i} d={arcPath(cx, cy + k, r, a.start, a.end)} fill="none"
          stroke={shadeHex(a.color, -0.40)} strokeWidth={th} strokeLinecap={cap} />
        );
      });
    }
    walls = layers;
    extrudeTop = <React.Fragment>
      {band(r + th * 0.5 - 1.2, 'rgba(255,255,255,0.40)', 2.2, 'et', 0, `url(#${glossId})`)}
      {band(r - th * 0.5 + 1, 'rgba(0,0,0,0.18)', 1.6, 'eb')}
    </React.Fragment>;
  }

  return (
    <g>
      <defs>
        <clipPath id={glossId}>
          <rect x={cx - r - th} y={cy - r - th} width={(r + th) * 2} height={r + th * 0.25} />
        </clipPath>
      </defs>

      {castShadow}
      {walls}
      {baseArcs}

      {mode === 'bevel' && <React.Fragment>
        {band(r - th * 0.30, 'rgba(0,0,0,0.22)', th * 0.42, 'bin')}
        {band(r + th * 0.31, 'rgba(255,255,255,0.42)', th * 0.40, 'bout')}
        {band(r - th * 0.5 + 0.6, 'rgba(0,0,0,0.14)', 1.4, 'bie')}
        {band(r - th * 0.04, 'rgba(255,255,255,0.30)', th * 0.16, 'bgl', 0, `url(#${glossId})`)}
      </React.Fragment>}

      {mode === 'gloss' && <React.Fragment>
        {band(r + th * 0.30, 'rgba(255,255,255,0.55)', th * 0.28, 'go')}
        {band(r - th * 0.30, 'rgba(0,0,0,0.18)', th * 0.30, 'gin')}
        {band(r + th * 0.08, 'rgba(255,255,255,0.50)', th * 0.46, 'gg', 0, `url(#${glossId})`)}
        {band(r - th * 0.20, 'rgba(255,255,255,0.20)', th * 0.16, 'gg2', 0, `url(#${glossId})`)}
      </React.Fragment>}

      {mode === 'soft' && <React.Fragment>
        {band(r + th * 0.20, 'rgba(255,255,255,0.50)', th * 0.36, 'sft', 0, `url(#${glossId})`)}
        {band(r + th * 0.30, 'rgba(0,0,0,0.10)', th * 0.34, 'sfb', th * 0.12)}
        {band(r - th * 0.28, 'rgba(0,0,0,0.07)', th * 0.30, 'sfi')}
      </React.Fragment>}

      {mode === 'inset' && <React.Fragment>
        {band(r + th * 0.30, 'rgba(0,0,0,0.22)', th * 0.30, 'iso', 0, `url(#${glossId})`)}
        {band(r - th * 0.30, 'rgba(0,0,0,0.16)', th * 0.26, 'isi', 0, `url(#${glossId})`)}
        {band(r + th * 0.30, 'rgba(255,255,255,0.36)', th * 0.30, 'isb', th * 0.16)}
      </React.Fragment>}

      {extrudeTop}
    </g>);

}

function Phone({ cfg }) {
  const C = cfg;
  const balance = C.income - C.expense;
  const ink = '#274034';
  const sub = 'rgba(39,64,52,0.42)';

  // donut art geometry — taller frame so icons clear the donut and the
  // cluster fills the space below the year strip (no empty band above).
  const artW = 384,artH = 476;
  const cx = artW / 2,cy = artH / 2;
  const cats = C.categories;
  const total = cats.reduce((s, c) => s + (c.pct || 0), 0) || 1;
  const D = C.donutSize,ringR = D / 2;

  // category-icon sizing, driven by cfg.iconScale
  const iconScale = C.iconScale || 1;
  const iconSize = Math.round(26 * iconScale);
  const circle = iconSize + 12;
  const pctSize = Math.round(13 * iconScale);
  const slotW = Math.max(circle, 46);
  const labelH = C.showPercent ? pctSize + 4 : 0;
  const hhBot = artH / 2 - (circle / 2 + labelH + 8);
  // lift the top row higher (closer to the year strip) — asymmetric frame
  const hhTop = Math.min(hhBot + 34, cy - circle / 2 - 2);
  const hw = 156;

  let cursor = 0;
  const placed = cats.map((c, i) => {
    const sweep = c.pct / total * 360;
    const mid = cursor + sweep / 2;
    cursor += sweep;
    const [rx, ry] = polar(cx, cy, ringR + 1, mid); // leader line root on ring
    const [fx, fy] = framePoint(i / cats.length, hw, hhTop, hhBot); // icon slot on frame
    return { ...c, mid, rx, ry, ix: cx + fx, iy: cy + fy };
  });

  return (
    <div style={{
      width: 384, height: 812, background: C.bg, position: 'relative', overflow: 'hidden',
      display: 'flex', flexDirection: 'column',
      fontFamily: "'Nunito', system-ui, sans-serif"
    }}>
      {/* ── status bar ── */}
      <div style={{
        height: 30, background: C.topbarColor, color: '#fff', display: 'flex',
        alignItems: 'center', justifyContent: 'space-between', padding: '0 14px', flexShrink: 0,
        fontSize: 12.5, fontWeight: 700
      }}>
        <span>{C.clock}</span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <svg width="16" height="11" viewBox="0 0 16 11" fill="#fff"><rect x="0" y="7" width="2.6" height="4" rx="0.6" /><rect x="4" y="4.5" width="2.6" height="6.5" rx="0.6" /><rect x="8" y="2.2" width="2.6" height="8.8" rx="0.6" /><rect x="12" y="0" width="2.6" height="11" rx="0.6" /></svg>
          <svg width="16" height="11" viewBox="0 0 16 11" fill="#fff"><path d="M8 2.4c2.1 0 4 .8 5.4 2.1l1-1.2A9.6 9.6 0 0 0 8 .9 9.6 9.6 0 0 0 1.6 3.3l1 1.2A8 8 0 0 1 8 2.4z" /><path d="M8 5.5c1.2 0 2.3.4 3.1 1.2l1-1.2A6.6 6.6 0 0 0 8 3.7a6.6 6.6 0 0 0-4.1 1.8l1 1.2A4.6 4.6 0 0 1 8 5.5z" /><circle cx="8" cy="9" r="1.4" /></svg>
          <span style={{ fontSize: 11.5 }}>{C.battery}%</span>
          <svg width="22" height="11" viewBox="0 0 22 11" fill="none"><rect x="0.5" y="0.5" width="18" height="10" rx="2.4" stroke="#fff" opacity="0.6" /><rect x="2" y="2" width={Math.max(2, 14 * C.battery / 100)} height="7" rx="1.2" fill="#fff" /><rect x="19.6" y="3" width="1.6" height="5" rx="0.8" fill="#fff" opacity="0.6" /></svg>
        </div>
      </div>

      {/* ── app bar ── */}
      <div style={{ background: C.topbarColor, padding: '4px 6px 12px', flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <button style={{ ...iconBtn, height: "40px", width: "40px" }}><Icon name="menu" size={22} color="#fff" stroke={2} /></button>
          <div style={{ flex: 1, marginLeft: 6 }}>
            <div style={{ ...{ color: '#fff', fontFamily: LOGO_FONTS[C.logoFont], fontSize: C.logoFont === 'script' ? 25 : 23, fontWeight: C.logoFont === 'script' ? 400 : 800, letterSpacing: C.logoFont === 'clean' ? -0.4 : 0, lineHeight: "1.3" }, fontSize: "25px", fontWeight: "400", lineHeight: "1.05", letterSpacing: "0.7px", height: "25px", width: "143px", padding: "0px", margin: "0px" }}>{C.appName}</div>
            <div style={{ color: 'rgba(255,255,255,0.85)', marginTop: 1, margin: "1px 0px 0px", fontWeight: "700", height: "14px", width: "180px", padding: "6px 0px 0px", fontFamily: "\"Times New Roman\"", fontSize: "17px" }}>{C.currencyName}</div>
          </div>
          <button style={iconBtn}><Icon name="search" size={21} color="#fff" stroke={2} /></button>
          <button style={{ ...iconBtn, width: "45px", fontWeight: "700", letterSpacing: "0px", lineHeight: "1.15" }}><Icon name="swap" size={21} color="#fff" stroke={2} /></button>
          <button style={{ ...iconBtn, width: "35px", height: "35px" }}><Icon name="more" size={21} color="#fff" /></button>
        </div>
      </div>

      {/* ── period strip ── */}
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'center', gap: 30, padding: '12px 0 2px', flexShrink: 0, position: 'relative', height: "40px" }}>
        <span style={{ position: 'absolute', left: 10, top: 12, color: sub, fontWeight: 700, fontSize: "20px" }}>{C.year - 1}</span>
        <span style={{ color: C.topbarColor, fontWeight: 800, fontSize: "25px" }}>{C.year}</span>
        <span style={{ position: 'absolute', right: 10, top: 12, color: sub, fontSize: 17, fontWeight: 700, opacity: 0 }}>{C.year + 1}</span>
      </div>

      {/* ── donut + icons ── */}
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 0, height: "611px" }}>
        <div style={{ position: 'relative', width: artW, height: artH }}>
          <svg width={artW} height={artH} viewBox={`0 0 ${artW} ${artH}`} style={{ position: 'absolute', inset: 0, height: "456px", width: "382px" }}>
            {/* leader lines */}
            {C.showLines && placed.map((p, i) => p.pct > 0 &&
            <line key={i} x1={p.rx} y1={p.ry} x2={p.ix} y2={p.iy}
            stroke={p.color} strokeWidth={1} opacity={0.55} />
            )}
            <Donut cfg={C} cx={cx} cy={cy} />
          </svg>

          {/* centre income / expense */}
          <div style={{
            position: 'absolute', left: cx - 90, top: cy - 34, textAlign: "center", width: "170px", height: "89px", lineHeight: "1.55", padding: "0px 19px 0px 0px", margin: "0px 0px 0px 10px", fontSize: "19px"
          }}>
            <div style={{ width: "160px", height: "20px" }}><Money value={C.income} symbol={C.symbol} color={C.incomeColor} size={19} kopecks={false} /></div>
            <div style={{ marginTop: 3, fontSize: "29px", width: "160px", margin: "5px 0px 0px 1px" }}><Money value={C.expense} symbol={C.symbol} color={C.expenseColor} size={19} kopecks={false} /></div>
          </div>

          {/* category icons + percent */}
          {C.showIcons && placed.map((p, i) =>
          <div key={i} style={{
            position: 'absolute', left: p.ix - slotW / 2, top: p.iy - circle / 2, width: slotW,
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, lineHeight: 1, color: "rgb(251, 251, 251)"
          }}>
              <div style={{
              width: circle, height: circle, background: C.bg,
              display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: "1", borderRadius: "42px"
            }}>
                <Icon name={p.key} size={iconSize} color={p.color} stroke={1.5} />
              </div>
              {C.showPercent &&
            <span style={{ color: p.color, fontSize: pctSize, fontWeight: 800, lineHeight: 1 }}>{Math.round(p.pct)}%</span>
            }
            </div>
          )}
        </div>
      </div>

      {/* ── balance pill ── */}
      <div style={{ display: 'flex', flexShrink: 0, borderStyle: "solid", alignItems: "stretch", height: "50px", justifyContent: "center", textAlign: "center", width: "382px", borderWidth: "0px", fontSize: "25px", margin: "-20px 0px 0px -2px", padding: "0px 0px 2px", gap: "10px" }}>
        <Icon name="menu" size={26} color={C.topbarColor} stroke={2.2} style={{ opacity: 0.7 }} />
        <div style={{
          flex: 1, maxWidth: 252, background: C.topbarColor, borderRadius: 8,
          color: '#fff',
          boxShadow: '0 2px 7px rgba(40,70,55,0.22)',
          whiteSpace: 'nowrap', width: "312px", fontWeight: "400", fontFamily: "\"Times New Roman\"", textAlign: "center", padding: "6px 16px 13px", fontSize: "25px"
        }}>
          Баланс <Money value={balance} symbol={C.symbol} color="#fff" size={19} />
        </div>
        <Icon name="menu" size={26} color={C.topbarColor} stroke={2.2} style={{ opacity: 0.7 }} />
      </div>

      {/* ── FABs ── */}
      <div style={{ display: 'flex', gap: 70, flexShrink: 0, alignItems: "stretch", textAlign: "center", fontSize: "13px", fontWeight: "300", opacity: "1", borderRadius: "0px", lineHeight: "1.4", letterSpacing: "0px", margin: "0px", borderStyle: "solid", borderWidth: "0px", height: "104px", justifyContent: "space-between", width: "388px", padding: "24px 0px 0px 45px" }}>
        {[
        { c: C.fabMinusColor || C.expenseColor, icon: 'remove' },
        { c: C.fabPlusColor || C.incomeColor, icon: 'add' }].
        map((b, i) =>
        <button key={i} style={{
          width: 100, height: 100, borderRadius: '50%', background: '#fff',
          border: `6px solid ${b.c}`, color: b.c, cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          boxShadow: '0 4px 12px rgba(40,70,55,0.14)', transition: 'transform .12s',
          padding: 0, margin: "-10px 42px 0px -8px"
        }}
        onMouseDown={(e) => e.currentTarget.style.transform = 'scale(0.93)'}
        onMouseUp={(e) => e.currentTarget.style.transform = 'scale(1)'}
        onMouseLeave={(e) => e.currentTarget.style.transform = 'scale(1)'}>
            <Icon name={b.icon} size={48} color={b.c} stroke={2.4} />
          </button>
        )}
      </div>

      {/* ── nav pill ── */}
      <div style={{ height: 22, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        <div style={{ width: 120, height: 4, borderRadius: 2, background: ink, opacity: 0.22 }} />
      </div>
    </div>);

}

const iconBtn = {
  width: 40, height: 40, borderRadius: '50%', border: 'none', background: 'transparent',
  display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', padding: 0
};

Object.assign(window, { Phone, polar, Donut, shadeHex, arcPath });