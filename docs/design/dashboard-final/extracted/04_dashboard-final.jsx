// neon/dashboard-final.jsx — точная реконструкция экрана дашборда MyMoney (репозиторий)
// Топбар + переключатель периода + плитки категорий + две FAB — как в приложении.
// Новое — только верхняя секция «баланс + график» (SecAurora из balance-variants.jsx).
// Палитра/размеры взяты из core/ui/theme (Color.kt, Spacing.kt, Typography.kt).

const MM = {
  bg: '#0A0E1C', // NeonBackground
  surface: '#111A2E', // NeonSurface / tileSurface
  textPrimary: '#E8EAF0', // NeonTextPrimary
  textSecondary: '#7C8290', // NeonTextSecondary
  mint: '#5BE3B0', // NeonMint (доход / индикатор)
  coral: '#FF8A80', // NeonCoral (расход)
  cyan: '#46B6E6' // NeonCyan (перевод)
};

// фиксированные настройки секции: Волна · Мульти · 30%
const T_FIXED = { graphType: 'wave', neonHue: 'multi', neonIntensity: 30 };

// ── верхний тулбар: меню · ‹ месяц › · ещё (одна строка) ───────
function RealTopBar({ data, setOffset }) {
  const iconBtn = (name, onClick, size = 24) =>
  <button onClick={onClick} style={{
    appearance: 'none', border: 'none', background: 'transparent', cursor: 'pointer',
    width: 48, height: 48, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, padding: "0px"
  }}>
      <CCIcon name={name} size={size} color={MM.textPrimary} />
    </button>;

  const parts = data.label.split(' ');
  const title = parts[1] === '2026' ? parts[0] : data.label;
  return (
    <div style={{ flexShrink: 0, background: MM.bg, display: 'flex', alignItems: 'center', minHeight: 56, padding: '6px 4px 10px' }}>
      {iconBtn('menu')}
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        {iconBtn('chevL', () => setOffset((o) => o - 1))}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
          <span style={{ fontSize: 22, fontWeight: 700, color: MM.textPrimary, textTransform: 'capitalize', lineHeight: 1 }}>{title}</span>
          <div style={{ width: 78, height: 4, borderRadius: 2, background: MM.mint }}></div>
        </div>
        {iconBtn('chevR', () => setOffset((o) => o + 1))}
      </div>
      {iconBtn('more')}
    </div>);

}

// ── плитка категории: иконка + название + сумма + прогресс снизу ──
function RealCategoryTile({ cat, idx, frac }) {
  const accent = catColor(cat, idx, T_FIXED); // мульти-неон по категории
  return (
    <div style={{
      position: 'relative', height: 76, borderRadius: 16, overflow: 'hidden',
      background: MM.surface, display: 'flex', alignItems: 'center', gap: 16, padding: '0 16px'
    }}>
      {/* неоновый чип-иконка (rounded 16, 44px) */}
      <div style={{
        width: 44, height: 44, borderRadius: 16, flexShrink: 0, position: 'relative', overflow: 'hidden',
        background: `radial-gradient(circle at 50% 42%, ${withA(accent, 0.24)}, ${withA(accent, 0)} 62%), ${MM.surface}`,
        boxShadow: `inset 0 0 0 1px ${withA(accent, 0.34)}`,
        display: 'flex', alignItems: 'center', justifyContent: 'center'
      }}>
        <CCIcon name={cat.icon} size={26} color={withA('#ffffff', 0.92)} style={{ filter: `drop-shadow(0 0 4px ${withA(accent, 0.7)})` }} />
      </div>
      <span style={{ flex: 1, minWidth: 0, fontSize: 16, fontWeight: 500, color: MM.textPrimary, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{cat.label}</span>
      <span style={{ fontSize: 16, fontWeight: 600, color: MM.textSecondary, fontVariantNumeric: 'tabular-nums' }}>{ccFmt(cat.amount)}</span>
      {/* тонкий прогресс-бар у нижней кромки */}
      <div style={{ position: 'absolute', left: 0, bottom: 0, height: 4, borderRadius: 2, width: `${Math.max(2, frac * 100)}%`, background: accent }}></div>
    </div>);

}

// ── три FAB-кнопки: − расход · ⇄ перевод (синяя) · + доход ─────
function RealFabs() {
  const fab = (color, icon, size = 88, glyph = 29) =>
  <button style={{
    appearance: 'none', cursor: 'pointer', width: size, height: size, borderRadius: '50%',
    background: MM.bg, border: `3.6px solid ${color}`,
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    boxShadow: `0 0 22px ${withA(color, 0.28)}, inset 0 0 16px ${withA(color, 0.12)}`
  }}>
      <CCIcon name={icon} size={glyph} color={color} />
    </button>;

  return (
    <div style={{ flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 26px 18px' }}>
      {fab(MM.coral, 'remove')}
      {fab(MM.cyan, 'swap')}
      {fab(MM.mint, 'add')}
    </div>);

}

// ── экран целиком ─────────────────────────────────────────────
function ScreenDashboardFinal({ data, setOffset }) {
  const cats = data.cats.slice(0, 6);
  const top = data.cats[0].amount;
  return (
    <CCPhone bg={MM.bg} ink={MM.textPrimary}>
      <RealTopBar data={data} setOffset={setOffset} />
      <div style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '8px 16px 4px' }}>
        <div style={{ marginBottom: 14 }}>
          <SecAurora data={data} t={T_FIXED} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {cats.map((c, i) =>
          <RealCategoryTile key={c.key} cat={c} idx={i} frac={c.amount / top} />
          )}
        </div>
      </div>
      <RealFabs />
    </CCPhone>);

}

Object.assign(window, { MM, RealTopBar, RealCategoryTile, RealFabs, ScreenDashboardFinal });