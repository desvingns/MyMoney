// neon/balance-variants.jsx — 6 дизайнов объединённой секции «баланс + доходы + расходы + график»
// Управляется новым Tweak (t.sectionVariant). График внутри = NeonChart (по умолчанию Волна).
// Зависит от neon-core.jsx (NeonChart, accentOf, neonGlow, textGlow, withA, lighten) и shared.jsx (ccFmt, ccFmtPlain, CCIcon)

const INC = '#3DF59B';      // доход — зелёный неон
const EXP = '#FF8A9B';      // расход — розово-красный

// маленькая метрика «стрелка + подпись + сумма»
function StatPill({ dir, label, value, color, t, big }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <span style={{ fontSize: 10.5, fontWeight: 700, letterSpacing: 0.5, color: withA('#fff', 0.5), textTransform: 'uppercase' }}>{label}</span>
      <span style={{ fontSize: big ? 16 : 14, fontWeight: 800, color, fontVariantNumeric: 'tabular-nums', textShadow: textGlow(color, t, 0.5) }}>
        {dir}{ccFmtPlain(value)}
      </span>
    </div>
  );
}

// ════════════ 1 · ПАНЕЛЬ (Pulse) ════════════
function SecPanel({ data, t }) {
  return (
    <div style={{ borderRadius: 20, padding: '16px 16px 12px', background: withA('#fff', 0.025), boxShadow: `inset 0 0 0 1px ${withA('#fff', 0.06)}` }}>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 4 }}>
        <div>
          <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, color: '#7B8499', textTransform: 'uppercase' }}>Расходы</div>
          <div style={{ fontSize: 24, fontWeight: 800, color: '#fff', letterSpacing: -0.5 }}>{ccFmt(data.expense)}</div>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, color: '#7B8499', textTransform: 'uppercase' }}>Баланс</div>
          <div style={{ fontSize: 16, fontWeight: 800, color: INC, textShadow: textGlow(INC, t, 0.7) }}>{ccFmtPlain(data.balance)} ₽</div>
        </div>
      </div>
      <div style={{ display: 'flex', gap: 16, margin: '0 0 6px' }}>
        <span style={{ fontSize: 12, fontWeight: 700, color: withA(INC, 0.95) }}>↑ {ccFmtPlain(data.income)}</span>
        <span style={{ fontSize: 12, fontWeight: 700, color: withA(EXP, 0.95) }}>↓ {ccFmtPlain(data.expense)}</span>
      </div>
      <NeonChart data={data} t={t} w={318} h={142} />
    </div>
  );
}

// ════════════ 2 · СТЕКЛО (Glass) ════════════
function SecGlass({ data, t }) {
  const acc = accentOf(t);
  return (
    <div style={{ position: 'relative', borderRadius: 24, padding: 18, overflow: 'hidden', background: `linear-gradient(150deg, ${withA(acc, 0.16)}, ${withA('#fff', 0.03)})`, boxShadow: `inset 0 0 0 1px ${withA('#fff', 0.12)}, ${neonGlow(acc, t, 0.7)}`, backdropFilter: 'blur(8px)' }}>
      <div style={{ position: 'absolute', top: -40, right: -30, width: 150, height: 150, borderRadius: '50%', background: `radial-gradient(circle, ${withA(acc, 0.4)}, transparent 65%)` }}></div>
      <div style={{ position: 'relative' }}>
        <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, color: withA('#fff', 0.6), textTransform: 'uppercase' }}>Свободно</div>
        <div style={{ fontSize: 30, fontWeight: 800, color: '#fff', letterSpacing: -0.8, marginTop: 2 }}>{ccFmtPlain(data.balance)} ₽</div>
        <div style={{ display: 'flex', gap: 22, margin: '12px 0 14px' }}>
          <StatPill dir="↑ " label="Доходы" value={data.income} color={INC} t={t} />
          <StatPill dir="↓ " label="Расходы" value={data.expense} color={EXP} t={t} />
        </div>
        <NeonChart data={data} t={t} w={314} h={120} />
      </div>
    </div>
  );
}

// ════════════ 3 · ТЕРМИНАЛ (mono) ════════════
function SecTerminal({ data, t }) {
  const mono = "'Space Mono', monospace";
  const row = (label, value, color) => (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', padding: '3px 0' }}>
      <span style={{ fontSize: 10, letterSpacing: 1, color: '#5E7A6E' }}>{label}</span>
      <span style={{ fontSize: 13, fontWeight: 700, color, fontVariantNumeric: 'tabular-nums' }}>{ccFmtPlain(value)}₽</span>
    </div>
  );
  return (
    <div style={{ borderRadius: 12, border: `1px solid ${withA('#fff', 0.1)}`, padding: '12px 14px', fontFamily: mono, background: withA('#fff', 0.015) }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 8 }}>
        <div>
          <div style={{ fontSize: 10, letterSpacing: 1, color: '#5E7A6E' }}>NET_BALANCE</div>
          <div style={{ fontSize: 24, fontWeight: 700, color: INC, textShadow: textGlow(INC, t, 0.7) }}>{ccFmtPlain(data.balance)}₽</div>
        </div>
        <div style={{ width: 132, height: 56 }}><NeonChart data={data} t={t} w={132} h={56} compact /></div>
      </div>
      <div style={{ borderTop: `1px dashed ${withA('#fff', 0.12)}`, paddingTop: 6 }}>
        {row('INCOME', data.income, withA(INC, 0.95))}
        {row('EXPENSE', data.expense, withA(EXP, 0.95))}
      </div>
    </div>
  );
}

// ════════════ 4 · ЛЕНТА (Spectrum) ════════════
function SecRibbon({ data, t }) {
  const acc = accentOf(t);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '12px 14px', borderRadius: 16, background: withA('#fff', 0.03), boxShadow: `inset 0 0 0 1px ${withA('#fff', 0.06)}` }}>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 10.5, fontWeight: 700, letterSpacing: 0.6, color: '#7B8499', textTransform: 'uppercase' }}>Всего трат</div>
          <div style={{ fontSize: 23, fontWeight: 800, color: '#fff', letterSpacing: -0.5 }}>{ccFmt(data.expense)}</div>
          <div style={{ fontSize: 12, fontWeight: 700, color: INC, marginTop: 3, textShadow: textGlow(INC, t, 0.5) }}>Баланс {ccFmtPlain(data.balance)} ₽</div>
        </div>
        <div style={{ width: 120, height: 70 }}><NeonChart data={data} t={t} w={120} h={70} compact /></div>
      </div>
      <div style={{ display: 'flex', gap: 10 }}>
        <div style={{ flex: 1, padding: '10px 13px', borderRadius: 13, background: withA(INC, 0.08), boxShadow: `inset 0 0 0 1px ${withA(INC, 0.25)}` }}>
          <StatPill dir="↑ " label="Доходы" value={data.income} color={INC} t={t} big />
        </div>
        <div style={{ flex: 1, padding: '10px 13px', borderRadius: 13, background: withA(EXP, 0.08), boxShadow: `inset 0 0 0 1px ${withA(EXP, 0.25)}` }}>
          <StatPill dir="↓ " label="Расходы" value={data.expense} color={EXP} t={t} big />
        </div>
      </div>
    </div>
  );
}

// ════════════ 5 · АВРОРА (центрированный герой) ════════════
function SecAurora({ data, t }) {
  const acc = accentOf(t);
  return (
    <div style={{ position: 'relative', borderRadius: 24, padding: '18px 18px 14px', overflow: 'hidden', textAlign: 'center', background: `radial-gradient(120% 90% at 50% 0%, ${withA(acc, 0.2)}, ${withA('#fff', 0.02)} 70%)`, boxShadow: `inset 0 0 0 1px ${withA(acc, 0.28)}, ${neonGlow(acc, t, 0.7)}` }}>
      <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 1, color: withA('#fff', 0.55), textTransform: 'uppercase' }}>Свободный баланс</div>
      <div style={{ fontSize: 36, fontWeight: 800, color: '#fff', letterSpacing: -1, margin: '2px 0 12px', textShadow: textGlow(acc, t, 0.6) }}>{ccFmtPlain(data.balance)} ₽</div>
      <div style={{ display: 'flex', justifyContent: 'center', gap: 10, marginBottom: 12 }}>
        <span style={{ fontSize: 12, fontWeight: 700, color: lighten(INC, 1.2), padding: '5px 12px', borderRadius: 20, background: withA(INC, 0.12), boxShadow: `inset 0 0 0 1px ${withA(INC, 0.3)}` }}>↑ Доходы {ccFmtPlain(data.income)}</span>
        <span style={{ fontSize: 12, fontWeight: 700, color: lighten(EXP, 1.2), padding: '5px 12px', borderRadius: 20, background: withA(EXP, 0.12), boxShadow: `inset 0 0 0 1px ${withA(EXP, 0.3)}` }}>↓ Расходы {ccFmtPlain(data.expense)}</span>
      </div>
      <NeonChart data={data} t={t} w={314} h={116} />
    </div>
  );
}

// ════════════ 6 · БЕНТО (плитки) ════════════
function SecBento({ data, t }) {
  const acc = accentOf(t);
  const tile = { borderRadius: 16, background: withA('#fff', 0.035), boxShadow: `inset 0 0 0 1px ${withA('#fff', 0.07)}`, padding: 13 };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={{ ...tile, display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: `linear-gradient(135deg, ${withA(acc, 0.14)}, ${withA('#fff', 0.02)})`, boxShadow: `inset 0 0 0 1px ${withA(acc, 0.25)}` }}>
        <div>
          <div style={{ fontSize: 10.5, fontWeight: 700, letterSpacing: 0.6, color: withA('#fff', 0.55), textTransform: 'uppercase' }}>Свободно</div>
          <div style={{ fontSize: 25, fontWeight: 800, color: '#fff', letterSpacing: -0.6 }}>{ccFmtPlain(data.balance)} ₽</div>
        </div>
        <div style={{ textAlign: 'right', fontSize: 12, fontWeight: 700, lineHeight: 1.7 }}>
          <div style={{ color: INC }}>↑ {ccFmtPlain(data.income)}</div>
          <div style={{ color: EXP }}>↓ {ccFmtPlain(data.expense)}</div>
        </div>
      </div>
      <div style={{ ...tile, paddingBottom: 8 }}>
        <div style={{ fontSize: 10.5, fontWeight: 800, letterSpacing: 1.2, textTransform: 'uppercase', color: withA('#fff', 0.4), marginBottom: 8 }}>Тренд за неделю</div>
        <NeonChart data={data} t={t} w={318} h={108} compact />
      </div>
    </div>
  );
}

const SECTION_VARIANTS = {
  panel: SecPanel, glass: SecGlass, terminal: SecTerminal,
  ribbon: SecRibbon, aurora: SecAurora, bento: SecBento,
};

function BalanceChartSection({ data, t }) {
  const Comp = SECTION_VARIANTS[(t && t.sectionVariant) || 'panel'] || SecPanel;
  return <Comp data={data} t={t} />;
}

Object.assign(window, {
  StatPill, SecPanel, SecGlass, SecTerminal, SecRibbon, SecAurora, SecBento,
  SECTION_VARIANTS, BalanceChartSection,
});
