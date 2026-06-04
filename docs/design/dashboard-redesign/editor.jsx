// editor.jsx — live design editor for the dashboard window.
// Exports to window: Editor, PALETTES, CAT_LABELS, defaultCfg

const PALETTES = {
  topbar:  ['#7AC794', '#50AB6F', '#3DA98A', '#4A8FCB', '#9C5BB8', '#5B6B7E', '#2E4A3A'],
  bg:      ['#F2FFF7', '#F0FBF4', '#FFFFFF', '#FAF7F0', '#EDF4FF', '#F6F4FA'],
  income:  ['#7AC794', '#50AB6F', '#3DA98A', '#4A8FCB'],
  expense: ['#F66561', '#E07A7A', '#D85A5A', '#E08A4A'],
  fab:     ['#50AB6F', '#7AC794', '#3DA98A', '#4A8FCB', '#9C5BB8', '#5B6B7E', '#E0A52C', '#F66561'],
  category:['#E07AAE', '#9C5BB8', '#7A9685', '#E07A7A', '#D85A5A', '#C9A227',
            '#E0A52C', '#F08A3E', '#4A8FCB', '#3A4F8C', '#3DA98A', '#7AC29A',
            '#D9A4A4', '#9CBBA8', '#4A5870', '#50AB6F'],
};

const CAT_LABELS = {
  food: 'Продукты', cafe: 'Кафе', transport: 'Транспорт', taxi: 'Такси',
  housing: 'Жильё', entertainment: 'Развлечения', bills: 'Распродажи', gifts: 'Подарки',
  phone: 'Телефон', sport: 'Спорт', health: 'Здоровье', hygiene: 'Гигиена',
  pets: 'Питомцы', clothing: 'Одежда',
};

const defaultCfg = {
  appName: 'MyMoney', currencyName: 'Рубли', symbol: '₽',
  clock: '17:22', battery: 48, year: 2026, logoFont: 'script',
  topbarColor: '#7AC794', bg: '#F2FFF7',
  incomeColor: '#50AB6F', expenseColor: '#F66561',
  income: 2444740.80, expense: 1699483.00,
  donutSize: 258, thickness: 50, gap: 5, iconScale: 1.7,
  donut3D: 'extrude', showPercent: true, showIcons: true, showLines: true, roundCaps: false,
  categories: [
    { id: 1,  key: 'food',          color: '#E07AAE', pct: 17 },
    { id: 2,  key: 'clothing',      color: '#9C5BB8', pct: 2 },
    { id: 3,  key: 'cafe',          color: '#7A9685', pct: 9 },
    { id: 4,  key: 'transport',     color: '#E07A7A', pct: 1 },
    { id: 5,  key: 'health',        color: '#D85A5A', pct: 1 },
    { id: 6,  key: 'bills',         color: '#C9A227', pct: 27 },
    { id: 7,  key: 'taxi',          color: '#E0A52C', pct: 0 },
    { id: 8,  key: 'entertainment', color: '#F08A3E', pct: 5 },
    { id: 9,  key: 'housing',       color: '#4A8FCB', pct: 5 },
    { id: 10, key: 'hygiene',       color: '#3A4F8C', pct: 4 },
    { id: 11, key: 'pets',          color: '#3DA98A', pct: 21 },
    { id: 12, key: 'sport',         color: '#7AC29A', pct: 3 },
    { id: 13, key: 'gifts',         color: '#D9A4A4', pct: 3 },
    { id: 14, key: 'phone',         color: '#9CBBA8', pct: 2 },
  ],
};

const E = {
  ink: '#1f2a26', sub: '#7a8a82', line: '#e7ece9', accent: '#3FA268', bg: '#fbfcfb',
};

// ── primitives ───────────────────────────────────────────────
function Field({ label, children, hint }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ fontSize: 12, fontWeight: 700, color: E.sub, marginBottom: 6, letterSpacing: 0.2 }}>{label}</div>
      {children}
      {hint && <div style={{ fontSize: 11, color: E.sub, marginTop: 4 }}>{hint}</div>}
    </div>
  );
}

function TextInput({ value, onChange, ...rest }) {
  return (
    <input value={value} onChange={(e) => onChange(e.target.value)} {...rest}
      style={{
        width: '100%', boxSizing: 'border-box', padding: '9px 11px', fontSize: 14,
        border: `1px solid ${E.line}`, borderRadius: 9, color: E.ink, background: '#fff',
        outline: 'none', fontFamily: 'inherit', fontWeight: 600,
      }} />
  );
}

function NumberInput({ value, onChange, step = 1, ...rest }) {
  return (
    <input type="number" value={value} step={step}
      onChange={(e) => onChange(e.target.value === '' ? 0 : parseFloat(e.target.value))} {...rest}
      style={{
        width: '100%', boxSizing: 'border-box', padding: '9px 11px', fontSize: 14,
        border: `1px solid ${E.line}`, borderRadius: 9, color: E.ink, background: '#fff',
        outline: 'none', fontFamily: 'inherit', fontWeight: 600,
      }} />
  );
}

function Swatches({ value, onChange, palette }) {
  const [open, setOpen] = React.useState(false);
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 7, alignItems: 'center' }}>
      {palette.map((c) => (
        <button key={c} onClick={() => onChange(c)} title={c} style={{
          width: 26, height: 26, borderRadius: 7, background: c, cursor: 'pointer',
          border: value.toLowerCase() === c.toLowerCase() ? `2.5px solid ${E.ink}` : '2.5px solid #fff',
          boxShadow: '0 0 0 1px rgba(0,0,0,0.08)', padding: 0,
        }} />
      ))}
      <label style={{
        width: 26, height: 26, borderRadius: 7, cursor: 'pointer', position: 'relative',
        border: '1px dashed #c4ccc8', display: 'flex', alignItems: 'center', justifyContent: 'center',
        background: palette.includes(value) ? '#fff' : value,
      }}>
        <span style={{ fontSize: 14, color: palette.includes(value) ? E.sub : '#fff', fontWeight: 700, lineHeight: 1 }}>+</span>
        <input type="color" value={value} onChange={(e) => onChange(e.target.value)}
          style={{ position: 'absolute', inset: 0, opacity: 0, cursor: 'pointer' }} />
      </label>
    </div>
  );
}

function Slider({ value, onChange, min, max, step = 1, suffix = '' }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
      <input type="range" min={min} max={max} step={step} value={value}
        onChange={(e) => onChange(parseFloat(e.target.value))}
        style={{ flex: 1, accentColor: E.accent }} />
      <span style={{ fontSize: 13, fontWeight: 700, color: E.ink, minWidth: 42, textAlign: 'right' }}>{Math.round(value * 100) / 100}{suffix}</span>
    </div>
  );
}

function Toggle({ value, onChange, label }) {
  return (
    <button onClick={() => onChange(!value)} style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%',
      padding: '8px 0', background: 'none', border: 'none', cursor: 'pointer', fontFamily: 'inherit',
    }}>
      <span style={{ fontSize: 13.5, fontWeight: 600, color: E.ink }}>{label}</span>
      <span style={{
        width: 40, height: 23, borderRadius: 12, background: value ? E.accent : '#d4dbd7',
        position: 'relative', transition: 'background .15s', flexShrink: 0,
      }}>
        <span style={{
          position: 'absolute', top: 2.5, left: value ? 19.5 : 2.5, width: 18, height: 18,
          borderRadius: '50%', background: '#fff', transition: 'left .15s', boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
        }} />
      </span>
    </button>
  );
}

function Segmented({ value, onChange, options }) {
  return (
    <div style={{ display: 'flex', gap: 4, background: '#eef2f0', borderRadius: 9, padding: 3 }}>
      {options.map((o) => (
        <button key={o.v} onClick={() => onChange(o.v)} style={{
          flex: 1, padding: '7px 4px', fontSize: 12.5, fontWeight: 700, borderRadius: 6, cursor: 'pointer',
          border: 'none', fontFamily: 'inherit',
          background: value === o.v ? '#fff' : 'transparent',
          color: value === o.v ? E.ink : E.sub,
          boxShadow: value === o.v ? '0 1px 3px rgba(0,0,0,0.12)' : 'none',
        }}>{o.label}</button>
      ))}
    </div>
  );
}

// ── 3D-style picker with live mini previews ──────────────────
const STYLE_3D = [
  { v: 'flat',    label: 'Плоский',   hint: 'без объёма' },
  { v: 'bevel',   label: 'Фаска',     hint: 'как в Monefy' },
  { v: 'gloss',   label: 'Глянец',    hint: 'стекло' },
  { v: 'extrude', label: 'Объём',     hint: 'реальная толщина' },
  { v: 'soft',    label: 'Мягкий',    hint: 'неоморфизм' },
  { v: 'inset',   label: 'Вдавленный',hint: 'эмбосс' },
];

function MiniRing({ mode }) {
  const Donut = window.Donut;
  const segs = [
    { key: 'a', color: '#7AC29A', pct: 34 },
    { key: 'b', color: '#E0A52C', pct: 33 },
    { key: 'c', color: '#E07AAE', pct: 33 },
  ];
  const cfg = { categories: segs, donutSize: 46, thickness: 15, gap: 0, donut3D: mode, roundCaps: false, _uid: 'mini-' + mode };
  return (
    <svg width={58} height={56} viewBox="0 0 58 56" style={{ display: 'block' }}>
      {Donut && <Donut cfg={cfg} cx={29} cy={26} />}
    </svg>
  );
}

function StyleGrid({ value, onChange }) {
  const cur = value === true ? 'bevel' : value === false ? 'flat' : value;
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8 }}>
      {STYLE_3D.map((s) => {
        const on = cur === s.v;
        return (
          <button key={s.v} onClick={() => onChange(s.v)} title={s.hint} style={{
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1, padding: '8px 4px 7px',
            borderRadius: 11, cursor: 'pointer', fontFamily: 'inherit',
            border: on ? `2px solid ${E.accent}` : `1px solid ${E.line}`,
            background: on ? '#f3faf5' : '#fff',
            boxShadow: on ? '0 2px 8px rgba(63,162,104,0.18)' : 'none',
          }}>
            <MiniRing mode={s.v} />
            <span style={{ fontSize: 11.5, fontWeight: 800, color: on ? E.accent : E.ink, lineHeight: 1.1 }}>{s.label}</span>
            <span style={{ fontSize: 9.5, fontWeight: 600, color: E.sub, lineHeight: 1.1 }}>{s.hint}</span>
          </button>
        );
      })}
    </div>
  );
}

function Section({ title, children, defaultOpen = true }) {
  const [open, setOpen] = React.useState(defaultOpen);
  return (
    <div style={{ borderBottom: `1px solid ${E.line}` }}>
      <button onClick={() => setOpen(!open)} style={{
        width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '15px 0', background: 'none', border: 'none', cursor: 'pointer', fontFamily: 'inherit',
      }}>
        <span style={{ fontSize: 13.5, fontWeight: 800, color: E.ink, letterSpacing: 0.2 }}>{title}</span>
        <span style={{ color: E.sub, transform: open ? 'rotate(90deg)' : 'none', transition: 'transform .15s' }}>
          <Icon name="chevronRight" size={18} color={E.sub} stroke={2.2} />
        </span>
      </button>
      {open && <div style={{ paddingBottom: 16 }}>{children}</div>}
    </div>
  );
}

// ── category row ─────────────────────────────────────────────
function CategoryRow({ cat, onChange, onRemove }) {
  const [picker, setPicker] = React.useState(null); // 'icon' | 'color' | null
  return (
    <div style={{ marginBottom: 8, border: `1px solid ${E.line}`, borderRadius: 11, padding: 8, background: '#fff' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <button onClick={() => setPicker(picker === 'icon' ? null : 'icon')} title="Иконка" style={{
          width: 38, height: 38, borderRadius: 9, border: `1px solid ${E.line}`, background: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', flexShrink: 0,
        }}>
          <Icon name={cat.key} size={24} color={cat.color} stroke={1.6} />
        </button>
        <input value={CAT_LABELS[cat.key] || cat.key} readOnly
          style={{
            flex: 1, minWidth: 0, padding: '8px 9px', fontSize: 13.5, fontWeight: 700,
            border: `1px solid ${E.line}`, borderRadius: 8, color: E.ink, background: '#fbfcfb',
            outline: 'none', fontFamily: 'inherit',
          }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 2, border: `1px solid ${E.line}`, borderRadius: 8, padding: '0 6px' }}>
          <input type="number" value={cat.pct} min={0} max={100}
            onChange={(e) => onChange({ pct: Math.max(0, Math.min(100, parseFloat(e.target.value) || 0)) })}
            style={{ width: 38, padding: '8px 0', fontSize: 13.5, fontWeight: 700, border: 'none', outline: 'none', textAlign: 'right', fontFamily: 'inherit', color: E.ink, background: 'transparent' }} />
          <span style={{ fontSize: 13, fontWeight: 700, color: E.sub }}>%</span>
        </div>
        <button onClick={() => setPicker(picker === 'color' ? null : 'color')} title="Цвет" style={{
          width: 28, height: 28, borderRadius: 7, background: cat.color, cursor: 'pointer',
          border: '2px solid #fff', boxShadow: '0 0 0 1px rgba(0,0,0,0.1)', flexShrink: 0, padding: 0,
        }} />
        <button onClick={onRemove} title="Удалить" style={{
          width: 28, height: 28, borderRadius: 7, border: `1px solid ${E.line}`, background: '#fff',
          color: '#c2554f', cursor: 'pointer', flexShrink: 0, fontSize: 18, lineHeight: 1, padding: 0,
        }}>×</button>
      </div>

      {picker === 'icon' && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5, marginTop: 8 }}>
          {CAT_ICON_KEYS.map((k) => (
            <button key={k} onClick={() => { onChange({ key: k }); setPicker(null); }} title={CAT_LABELS[k]} style={{
              width: 34, height: 34, borderRadius: 8, cursor: 'pointer',
              border: cat.key === k ? `2px solid ${E.accent}` : `1px solid ${E.line}`,
              background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 0,
            }}>
              <Icon name={k} size={21} color={E.ink} stroke={1.6} />
            </button>
          ))}
        </div>
      )}
      {picker === 'color' && (
        <div style={{ marginTop: 8 }}>
          <Swatches value={cat.color} onChange={(c) => onChange({ color: c })} palette={PALETTES.category} />
        </div>
      )}
    </div>
  );
}

// ── main editor ──────────────────────────────────────────────
function Editor({ cfg, set, reset }) {
  const total = cfg.categories.reduce((s, c) => s + (c.pct || 0), 0);
  const balance = cfg.income - cfg.expense;
  const fmtRub = (n) => n.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  const updateCat = (id, patch) =>
    set({ categories: cfg.categories.map((c) => c.id === id ? { ...c, ...patch } : c) });
  const removeCat = (id) =>
    set({ categories: cfg.categories.filter((c) => c.id !== id) });
  const addCat = () => {
    const used = cfg.categories.map((c) => c.key);
    const free = CAT_ICON_KEYS.find((k) => !used.includes(k)) || 'food';
    const color = PALETTES.category[cfg.categories.length % PALETTES.category.length];
    const id = Math.max(0, ...cfg.categories.map((c) => c.id)) + 1;
    set({ categories: [...cfg.categories, { id, key: free, color, pct: 5 }] });
  };

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', background: '#fff', fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* header */}
      <div style={{ padding: '18px 22px 14px', borderBottom: `1px solid ${E.line}`, display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
        <div>
          <div style={{ fontSize: 16, fontWeight: 800, color: E.ink, letterSpacing: -0.2 }}>Редактор дизайна</div>
          <div style={{ fontSize: 12, color: E.sub, marginTop: 2 }}>Dashboard · живой предпросмотр</div>
        </div>
        <button onClick={reset} style={{
          padding: '8px 14px', fontSize: 12.5, fontWeight: 700, borderRadius: 9, cursor: 'pointer',
          border: `1px solid ${E.line}`, background: '#fff', color: E.sub, fontFamily: 'inherit',
        }}>Сброс</button>
      </div>

      {/* scroll body */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 22px 40px' }}>
        <Section title="Бренд">
          <Field label="Название"><TextInput value={cfg.appName} onChange={(v) => set({ appName: v })} /></Field>
          <Field label="Подпись (валюта)"><TextInput value={cfg.currencyName} onChange={(v) => set({ currencyName: v })} /></Field>
          <div style={{ display: 'flex', gap: 12 }}>
            <div style={{ width: 90 }}><Field label="Символ"><TextInput value={cfg.symbol} onChange={(v) => set({ symbol: v })} /></Field></div>
            <div style={{ flex: 1 }}><Field label="Год"><NumberInput value={cfg.year} onChange={(v) => set({ year: Math.round(v) })} /></Field></div>
          </div>
          <Field label="Шрифт логотипа">
            <Segmented value={cfg.logoFont} onChange={(v) => set({ logoFont: v })}
              options={[{ v: 'script', label: 'Script' }, { v: 'rounded', label: 'Округлый' }, { v: 'clean', label: 'Строгий' }]} />
          </Field>
        </Section>

        <Section title="Цвета">
          <Field label="Шапка и акценты"><Swatches value={cfg.topbarColor} onChange={(v) => set({ topbarColor: v })} palette={PALETTES.topbar} /></Field>
          <Field label="Фон"><Swatches value={cfg.bg} onChange={(v) => set({ bg: v })} palette={PALETTES.bg} /></Field>
          <Field label="Доход (зелёный)"><Swatches value={cfg.incomeColor} onChange={(v) => set({ incomeColor: v })} palette={PALETTES.income} /></Field>
          <Field label="Расход (красный)"><Swatches value={cfg.expenseColor} onChange={(v) => set({ expenseColor: v })} palette={PALETTES.expense} /></Field>
          <Field label="Кнопка «−» (расход)" hint="Цвет круглой кнопки слева"><Swatches value={cfg.fabMinusColor || cfg.expenseColor} onChange={(v) => set({ fabMinusColor: v })} palette={PALETTES.fab} /></Field>
          <Field label="Кнопка «+» (доход)" hint="Цвет круглой кнопки справа"><Swatches value={cfg.fabPlusColor || cfg.incomeColor} onChange={(v) => set({ fabPlusColor: v })} palette={PALETTES.fab} /></Field>
        </Section>

        <Section title="Суммы">
          <Field label="Доход"><NumberInput value={cfg.income} step={1000} onChange={(v) => set({ income: v })} /></Field>
          <Field label="Расход"><NumberInput value={cfg.expense} step={1000} onChange={(v) => set({ expense: v })} /></Field>
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            background: '#f4faf6', borderRadius: 10, padding: '11px 14px', marginTop: 2,
          }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: E.sub }}>Баланс</span>
            <span style={{ fontSize: 15, fontWeight: 800, color: balance < 0 ? cfg.expenseColor : E.accent }}>{fmtRub(balance)} {cfg.symbol}</span>
          </div>
        </Section>

        <Section title="Диаграмма">
          <Field label="Стиль 3D-эффекта" hint="Шесть вариантов объёма диаграммы">
            <StyleGrid value={cfg.donut3D} onChange={(v) => set({ donut3D: v })} />
          </Field>
          <Field label="Диаметр"><Slider value={cfg.donutSize} onChange={(v) => set({ donutSize: v })} min={170} max={280} suffix="px" /></Field>
          <Field label="Толщина кольца"><Slider value={cfg.thickness} onChange={(v) => set({ thickness: v })} min={14} max={64} suffix="px" /></Field>
          <Field label="Зазор между секторами"><Slider value={cfg.gap} onChange={(v) => set({ gap: v })} min={0} max={10} step={0.5} suffix="°" /></Field>
          <Field label="Размер иконок и процентов" hint="Масштабирует все иконки категорий и подписи %">
            <Slider value={cfg.iconScale} onChange={(v) => set({ iconScale: v })} min={0.7} max={2.2} step={0.05} suffix="×" />
          </Field>
          <div style={{ marginTop: 4 }}>
            <Toggle value={cfg.roundCaps} onChange={(v) => set({ roundCaps: v })} label="Скруглённые концы секторов" />
            <Toggle value={cfg.showIcons} onChange={(v) => set({ showIcons: v })} label="Иконки категорий" />
            <Toggle value={cfg.showPercent} onChange={(v) => set({ showPercent: v })} label="Проценты" />
            <Toggle value={cfg.showLines} onChange={(v) => set({ showLines: v })} label="Линии-выноски" />
          </div>
        </Section>

        <Section title="Категории" defaultOpen={false}>
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10,
            fontSize: 12, fontWeight: 700, color: total === 100 ? E.accent : '#c79a2a',
          }}>
            <span>Сумма долей: {total}%</span>
            {total !== 100 && <span>≠ 100%</span>}
          </div>
          {cfg.categories.map((c) => (
            <CategoryRow key={c.id} cat={c}
              onChange={(patch) => updateCat(c.id, patch)}
              onRemove={() => removeCat(c.id)} />
          ))}
          <button onClick={addCat} style={{
            width: '100%', padding: '10px', fontSize: 13, fontWeight: 700, borderRadius: 10, cursor: 'pointer',
            border: `1px dashed #c4ccc8`, background: '#fbfcfb', color: E.accent, fontFamily: 'inherit', marginTop: 4,
          }}>+ Добавить категорию</button>
        </Section>
      </div>
    </div>
  );
}

Object.assign(window, { Editor, PALETTES, CAT_LABELS, defaultCfg });
