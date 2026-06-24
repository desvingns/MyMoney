// concepts/shared.jsx — данные, иконки, оболочка телефона для всех концептов
// Экспортирует в window: CCATS, ccMonthData, ccFmt, ccFmtSign, CCIcon, CCPhone,
// CCStatusBar, CCFabs, CCPeriodNav, ccShade, ccMix

// ── 12 категорий: цвета из core/ui/theme/Color.kt ─────────────
const CCATS = [
  { key: 'food',          label: 'Продукты',    color: '#E07AAE', base: 15200, icon: 'basket' },
  { key: 'housing',       label: 'Жильё',       color: '#4A8FCB', base: 12000, icon: 'home' },
  { key: 'cafe',          label: 'Кафе',        color: '#7A9685', base: 7800,  icon: 'restaurant' },
  { key: 'transport',     label: 'Транспорт',   color: '#E07A7A', base: 5400,  icon: 'bus' },
  { key: 'entertainment', label: 'Развлечения', color: '#F08A3E', base: 4200,  icon: 'bar' },
  { key: 'clothing',      label: 'Одежда',      color: '#9C5BB8', base: 2750,  icon: 'shirt' },
  { key: 'health',        label: 'Здоровье',    color: '#D85A5A', base: 2300,  icon: 'health' },
  { key: 'pets',          label: 'Питомцы',     color: '#3DA98A', base: 1900,  icon: 'pet' },
  { key: 'phone',         label: 'Связь',       color: '#8FA6C9', base: 1100,  icon: 'phone' },
  { key: 'sport',         label: 'Спорт',       color: '#7AC29A', base: 950,   icon: 'sport' },
  { key: 'gifts',         label: 'Подарки',     color: '#D9A4A4', base: 800,   icon: 'gift' },
  { key: 'bills',         label: 'Быт и счета', color: '#C9A227', base: 700,   icon: 'receipt' },
];

const CC_MONTHS = ['Январь','Февраль','Март','Апрель','Май','Июнь','Июль','Август','Сентябрь','Октябрь','Ноябрь','Декабрь'];

function ccRand(seed) { const x = Math.sin(seed * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); }

// offset 0 = Апрель 2026 (точные базовые суммы); count = 6 | 12
function ccMonthData(offset, count) {
  const mIdx = (3 + offset % 12 + 12) % 12;
  const year = 2026 + Math.floor((3 + offset) / 12);
  const cats = CCATS.slice(0, count).map((c, i) => {
    const f = offset === 0 ? 1 : 0.55 + ccRand(offset * 31 + i * 7) * 0.9;
    return { ...c, amount: Math.round(c.base * f / 10) * 10 };
  }).sort((a, b) => b.amount - a.amount);
  const expense = cats.reduce((s, c) => s + c.amount, 0);
  const income = offset === 0 ? 85000 : Math.round((70000 + ccRand(offset * 13) * 30000) / 500) * 500;
  const total = expense;
  cats.forEach((c) => { c.pct = c.amount / total; });
  return {
    label: CC_MONTHS[mIdx] + ' ' + year,
    prev: CC_MONTHS[(mIdx + 11) % 12],
    next: CC_MONTHS[(mIdx + 1) % 12],
    income, expense, balance: income - expense, cats,
  };
}

const ccFmt = (n) => Math.round(n).toLocaleString('ru-RU').replace(/,/g, '\u202F') + '\u00A0₽';
const ccFmtPlain = (n) => Math.round(n).toLocaleString('ru-RU').replace(/,/g, '\u202F');
const ccFmtSign = (n) => (n >= 0 ? '+' : '−') + ccFmt(Math.abs(n));

// ── цветовые помощники ────────────────────────────────────────
function ccHex(c) { const m = c.replace('#',''); return [parseInt(m.slice(0,2),16), parseInt(m.slice(2,4),16), parseInt(m.slice(4,6),16)]; }
function ccShade(color, f) { // f<1 темнее, f>1 светлее (к белому)
  const [r,g,b] = ccHex(color);
  const ch = (v) => Math.round(f <= 1 ? v * f : v + (255 - v) * (f - 1));
  return `rgb(${ch(r)},${ch(g)},${ch(b)})`;
}
function ccMix(a, b, t) {
  const A = ccHex(a), B = ccHex(b);
  return `rgb(${A.map((v,i)=>Math.round(v+(B[i]-v)*t)).join(',')})`;
}

// ── иконки (Material glyphs) ──────────────────────────────────
const CC_ICONS = {
  menu: 'M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z',
  search: 'M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z',
  swap: 'M6.99 11 3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z',
  more: 'M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  remove: 'M19 13H5v-2h14v2z',
  chevR: 'M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z',
  chevL: 'M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z',
  up: 'M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z',
  down: 'M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z',
  wallet: 'M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z',
  basket: 'M17.21 9l-4.38-6.56a.993.993 0 0 0-.83-.42c-.33 0-.64.16-.83.43L6.79 9H2c-.55 0-1 .45-1 1 0 .09.01.18.04.27l2.54 9.27c.23.84 1 1.46 1.92 1.46h13c.92 0 1.69-.62 1.93-1.46l2.54-9.27L23 10c0-.55-.45-1-1-1h-4.79zM9 9l3-4.4L15 9H9zm3 8c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z',
  home: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z',
  restaurant: 'M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 13 11.12 13 9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z',
  bus: 'M4 16c0 .88.39 1.67 1 2.22V20c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h8v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1.78c.61-.55 1-1.34 1-2.22V6c0-3.5-3.58-4-8-4s-8 .5-8 4v10zm3.5 1c-.83 0-1.5-.67-1.5-1.5S6.67 14 7.5 14s1.5.67 1.5 1.5S8.33 17 7.5 17zm9 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm1.5-6H6V6h12v5z',
  bar: 'M21 5V3H3v2l8 9v5H6v2h12v-2h-5v-5l8-9zM7.43 7L5.66 5h12.69l-1.78 2H7.43z',
  shirt: 'M21.6 18.2 13 11.75v-.91c1.65-.49 2.8-2.17 2.43-4.05-.26-1.31-1.3-2.4-2.61-2.7C10.54 3.57 8.5 5.3 8.5 7.5h2c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5c0 .84-.69 1.52-1.53 1.5-.54-.01-.97.45-.97.99v1.76L2.4 18.2c-.77.58-.36 1.8.6 1.8h18c.96 0 1.37-1.22.6-1.8z',
  health: 'M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z',
  pet: 'M4.5 12c1.38 0 2.5-1.12 2.5-2.5S5.88 7 4.5 7 2 8.12 2 9.5 3.12 12 4.5 12zm4-4C9.88 8 11 6.88 11 5.5S9.88 3 8.5 3 6 4.12 6 5.5 7.12 8 8.5 8zm7 0C16.88 8 18 6.88 18 5.5S16.88 3 15.5 3 13 4.12 13 5.5 14.12 8 15.5 8zm4 4c1.38 0 2.5-1.12 2.5-2.5S20.88 7 19.5 7 17 8.12 17 9.5s1.12 2.5 2.5 2.5zm-2.04 3.16c-.87-1.02-1.6-1.89-2.48-2.91-.46-.54-1.05-1.08-1.75-1.32-.11-.04-.22-.07-.33-.09-.25-.04-.52-.04-.78-.04s-.53 0-.79.05c-.11.02-.22.05-.33.09-.7.24-1.28.78-1.75 1.32-.87 1.02-1.6 1.89-2.48 2.91-1.31 1.31-2.92 2.76-2.62 4.79.29 1.02 1.02 2.03 2.33 2.32.73.15 3.06-.44 5.54-.44h.18c2.48 0 4.81.58 5.54.44 1.31-.29 2.04-1.3 2.33-2.32.31-2.04-1.3-3.49-2.61-4.8z',
  phone: 'M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z',
  sport: 'M20.57 14.86 22 13.43 20.57 12 17 15.57 8.43 7 12 3.43 10.57 2 9.14 3.43 7.71 2 5.57 4.14 4.14 2.71 2.71 4.14l1.43 1.43L2 7.71l1.43 1.43L2 10.57 3.43 12 7 8.43 15.57 17 12 20.57 13.43 22l1.43-1.43L16.29 22l2.14-2.14 1.43 1.43 1.43-1.43-1.43-1.43L22 16.29z',
  gift: 'M20 6h-2.18c.11-.31.18-.65.18-1 0-1.66-1.34-3-3-3-1.05 0-1.96.54-2.5 1.35l-.5.67-.5-.68C10.96 2.54 10.05 2 9 2 7.34 2 6 3.34 6 5c0 .35.07.69.18 1H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-5-2c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zM9 4c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm11 15H4v-2h16v2zm0-5H4V8h5.08L7 10.83 8.62 12 11 8.76l1-1.36 1 1.36L15.38 12 17 10.83 14.92 8H20v6z',
  receipt: 'M18 17H6v-2h12v2zm0-4H6v-2h12v2zm0-4H6V7h12v2zM3 22l1.5-1.5L6 22l1.5-1.5L9 22l1.5-1.5L12 22l1.5-1.5L15 22l1.5-1.5L18 22l1.5-1.5L21 22V2l-1.5 1.5L18 2l-1.5 1.5L15 2l-1.5 1.5L12 2l-1.5 1.5L9 2 7.5 3.5 6 2 4.5 3.5 3 2v20z',
};

function CCIcon({ name, size = 24, color = 'currentColor', style }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={color} style={{ display: 'block', flexShrink: 0, ...style }}>
      <path d={CC_ICONS[name] || ''}></path>
    </svg>
  );
}

// ── статус-бар + оболочка ─────────────────────────────────────
function CCStatusBar({ color }) {
  return (
    <div style={{ height: 38, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 22px', flexShrink: 0 }}>
      <span style={{ fontSize: 14, fontWeight: 600, color, letterSpacing: 0.2 }}>9:41</span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <svg width="17" height="12" viewBox="0 0 17 12" fill={color}><rect x="0" y="7" width="3" height="5" rx="1"></rect><rect x="4.5" y="4.5" width="3" height="7.5" rx="1"></rect><rect x="9" y="2" width="3" height="10" rx="1"></rect><rect x="13.5" y="0" width="3" height="12" rx="1"></rect></svg>
        <svg width="24" height="12" viewBox="0 0 24 12" fill="none"><rect x="0.5" y="0.5" width="20" height="11" rx="3" stroke={color} opacity="0.5"></rect><rect x="2" y="2" width="15" height="8" rx="1.5" fill={color}></rect><rect x="21.5" y="3.5" width="1.8" height="5" rx="0.9" fill={color} opacity="0.5"></rect></svg>
      </div>
    </div>
  );
}

function CCPhone({ children, bg, ink, font }) {
  return (
    <div style={{
      width: 390, height: 844, background: bg, position: 'relative', overflow: 'hidden',
      display: 'flex', flexDirection: 'column', borderRadius: 28,
      fontFamily: font || "'Manrope', system-ui, sans-serif",
    }}>
      <CCStatusBar color={ink}></CCStatusBar>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, position: 'relative' }}>
        {children}
      </div>
      <div style={{ height: 22, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        <div style={{ width: 120, height: 4, borderRadius: 2, background: ink, opacity: 0.25 }}></div>
      </div>
    </div>
  );
}

// ── два FAB (− расход / + доход) ──────────────────────────────
function CCFabs({ minusBg, plusBg, fg = '#fff', size = 64, gap = 84, style }) {
  const fab = (bg, icon) => (
    <div style={{
      width: size, height: size, borderRadius: size / 2, background: bg, color: fg,
      display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
      boxShadow: '0 6px 16px rgba(0,0,0,0.22)',
    }}>
      <CCIcon name={icon} size={size * 0.46}></CCIcon>
    </div>
  );
  return (
    <div style={{ display: 'flex', justifyContent: 'center', gap, padding: '10px 0 14px', flexShrink: 0, ...style }}>
      {fab(minusBg, 'remove')}
      {fab(plusBg, 'add')}
    </div>
  );
}

// ── навигация по периоду ──────────────────────────────────────
function CCPeriodNav({ data, onPrev, onNext, ink, dim, style }) {
  const btn = (icon, fn) => (
    <div onClick={fn} style={{ width: 40, height: 40, borderRadius: 20, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: dim }}>
      <CCIcon name={icon} size={22}></CCIcon>
    </div>
  );
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', ...style }}>
      {btn('chevL', onPrev)}
      <span style={{ fontSize: 16, fontWeight: 700, color: ink, letterSpacing: 0.3 }}>{data.label}</span>
      {btn('chevR', onNext)}
    </div>
  );
}

// ── бюджетные метрики «запаса» ────────────────────────────────
// Допущение демо: мы примерно на 20-м дне 30-дневного месяца.
function ccBudget(data) {
  const daysIn = 30, daysElapsed = 20, daysLeft = daysIn - daysElapsed;
  const frac = Math.min(1, data.expense / data.income);          // доля дохода потрачена
  const perDaySoFar = data.expense / daysElapsed;                 // средний темп трат
  const perDayLeft = Math.max(0, data.balance) / daysLeft;        // можно тратить в день
  const runwayDays = perDaySoFar > 0 ? data.balance / perDaySoFar : daysLeft; // дней запаса
  const planFrac = daysElapsed / daysIn;                          // где «по плану» должны быть
  return { daysIn, daysElapsed, daysLeft, frac, perDaySoFar, perDayLeft, runwayDays, planFrac };
}

// светофор по доле потраченного
function ccGauge(frac, green, amber, red) {
  return frac < 0.7 ? green : frac < 0.92 ? amber : red;
}

// ── анимация «заполнения»: 0 → target за ~900мс, ease-out ─────
function ccUseGrow(target, dep) {
  const reduce = typeof window !== 'undefined' && window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const [v, setV] = React.useState(reduce ? target : 0);
  React.useEffect(() => {
    if (reduce) { setV(target); return; }
    let raf, start, done = false;
    const dur = 900;
    const tick = (ts) => {
      if (!start) start = ts;
      const p = Math.min(1, (ts - start) / dur);
      const e = 1 - Math.pow(1 - p, 3);
      setV(target * e);
      if (p < 1) raf = requestAnimationFrame(tick); else done = true;
    };
    setV(0);
    raf = requestAnimationFrame(tick);
    // гарантия финального значения, если rAF задросселирован при пересборке варианта
    const ft = setTimeout(() => { if (!done) setV(target); }, dur + 200);
    return () => { cancelAnimationFrame(raf); clearTimeout(ft); };
    // eslint-disable-next-line
  }, [target, dep]);
  return v;
}

Object.assign(window, { CCATS, CC_ICONS, ccMonthData, ccFmt, ccFmtPlain, ccFmtSign, CCIcon, CCPhone, CCStatusBar, CCFabs, CCPeriodNav, ccShade, ccMix, ccBudget, ccGauge, ccUseGrow });
