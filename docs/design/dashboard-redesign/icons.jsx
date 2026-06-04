// icons.jsx — line-style category + UI icons for the MyMoney dashboard.
// Category icons match the real app (core/designsystem CategoryIcons.kt):
// hygiene / pets / health / clothing use the app's exact stroke path data;
// the rest are authored in the same 1.6px round-stroke style.
// Exports to window: Icon, CAT_ICON_KEYS

const STROKE_ICONS = {
  // ── UI ───────────────────────────────────────────────
  menu: 'M3.5 7H20.5 M3.5 12H20.5 M3.5 16.5H20.5',
  search: 'M10.5 17a6.5 6.5 0 1 1 0-13 6.5 6.5 0 0 1 0 13 Z M15.4 15.4 L20.5 20.5',
  swap: 'M4 9H17 M14 6 L17 9 L14 12 M20 15 H7 M10 12 L7 15 L10 18',
  add: 'M12 5V19 M5 12H19',
  remove: 'M5 12H19',
  chevronLeft: 'M14.5 6 L9 12 L14.5 18',
  chevronRight: 'M9.5 6 L15 12 L9.5 18',
  wallet: 'M4 7.5 A1.5 1.5 0 0 1 5.5 6 H18 A1.5 1.5 0 0 1 19.5 7.5 V17 A1.5 1.5 0 0 1 18 18.5 H5.5 A1.5 1.5 0 0 1 4 17 Z M14 11 H20 V14 H14 A1.5 1.5 0 0 1 14 11 Z',

  // ── Category ─────────────────────────────────────────
  // gifts — CardGiftcard
  gifts: 'M3.5 8.5H20.5V12H3.5Z M5 12V20H19V12 M12 8.5V20 M12 8.5C10 4.7 5.7 6 8.4 8.5 M12 8.5C14 4.7 18.3 6 15.6 8.5',
  // phone — Call (handset)
  phone: 'M6.6 4C5.6 4 5 4.8 5 5.8 C5 13 11 19 18.2 19 C19.2 19 20 18.4 20 17.4 L20 14.8 C20 14.2 19.6 13.8 19 13.7 L16.1 13.2 C15.6 13.1 15.1 13.3 14.8 13.7 L13.8 14.9 C11.6 13.8 10.2 12.4 9.1 10.2 L10.3 9.2 C10.7 8.9 10.9 8.4 10.8 7.9 L10.3 5 C10.2 4.4 9.8 4 9.2 4 Z',
  // food — ShoppingBasket
  food: 'M9 9V7.2 A3 3 0 0 1 15 7.2 V9 M4.5 9H19.5 L18 19 A1.2 1.2 0 0 1 16.8 20 H7.2 A1.2 1.2 0 0 1 6 19 Z M9.5 12.2 L10 17 M14.5 12.2 L14 17',
  // transport — Train
  transport: 'M7 4H17 A2 2 0 0 1 19 6 V14.5 A2 2 0 0 1 17 16.5 H7 A2 2 0 0 1 5 14.5 V6 A2 2 0 0 1 7 4 Z M5 10.5H19 M8.5 16.5 L6.5 20 M15.5 16.5 L17.5 20 M8.5 13.3 L8.5 13.31 M15.5 13.3 L15.5 13.31',
  // cafe — Restaurant (fork + knife)
  cafe: 'M7 12V21 M5 3V8 A2 2 0 0 0 9 8 V3 M7 8V12 M16.5 3 C18.6 4.3 18.6 9.7 16.5 11 V21',
  // entertainment — LocalBar (martini)
  entertainment: 'M4.5 5.5H19.5 L12 13 Z M12 13V19 M8.5 19H15.5 M19.5 5.5 L16.2 8.8',
  // taxi — LocalTaxi
  taxi: 'M10 4H14 V6.5 M6 11 L7.4 6.8 A1 1 0 0 1 8.4 6 H15.6 A1 1 0 0 1 16.6 6.8 L18 11 M4.8 11H19.2 V15.5 A1 1 0 0 1 18.2 16.5 H5.8 A1 1 0 0 1 4.8 15.5 Z M8 16.5 L8 18.5 M16 16.5 L16 18.5',
  // housing — Home
  housing: 'M4 11.5 L12 4 L20 11.5 M6.2 9.8 V20 H17.8 V9.8 M10 20 V14 H14 V20',
  // sport — DirectionsRun
  sport: 'M15.6 5.6 A1.9 1.9 0 1 1 15.59 5.6 Z M15.5 9 L11.8 13.2 L14.4 15.2 L13.2 20 M11.8 13.2 L8.4 11.6 M14.4 15.2 L18.6 16 M15.5 9 L12 8.2 L9.2 10',
  // bills — LocalOffer (tag)
  bills: 'M3.2 11 V4.6 A1.4 1.4 0 0 1 4.6 3.2 H11 L20.8 13 A1.4 1.4 0 0 1 20.8 15 L15 20.8 A1.4 1.4 0 0 1 13 20.8 Z M7 7 L7.01 7',

  // ── exact app paths (CategoryIcons.kt) ───────────────
  hygiene: 'M4 13 L14 13 L14 18 A2 2 0 0 1 12 20 L6 20 A2 2 0 0 1 4 18 Z M14 14.5 L18 14.5 M18 14.5 L18 5 A1 1 0 0 1 19 4 L20 4 M5.5 13 L5.5 9 M8 13 L8 9 M10.5 13 L10.5 9 M12.5 13 L12.5 9',
  pets: 'M7 5 L9 9 L15 9 L17 5 L17 12 A5 5 0 0 1 7 12 Z M9.5 13 L9.5 13.01 M14.5 13 L14.5 13.01 M12 15 L11 17 M12 15 L13 17',
  health: 'M12 3 A2 2 0 0 1 14 5 L14 14.5 A4 4 0 1 1 10 14.5 L10 5 A2 2 0 0 1 12 3 Z M12 9 L12 16',
  clothing: 'M9 4 L15 4 L20 7 L18 11 L16 10 L16 20 L8 20 L8 10 L6 11 L4 7 Z M9 4 A3 2 0 0 0 15 4'
};

// dot-style icons (rendered as filled circles, not strokes)
const DOT_ICONS = {
  more: [[12, 5], [12, 12], [12, 19]]
};

const CAT_ICON_KEYS = [
'food', 'cafe', 'transport', 'taxi', 'housing', 'entertainment',
'bills', 'gifts', 'phone', 'sport', 'health', 'hygiene', 'pets', 'clothing'];


function Icon({ name, size = 24, color = 'currentColor', stroke = 1.6, style }) {
  if (DOT_ICONS[name]) {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill={color}
      style={{ display: 'block', flexShrink: 0, ...style, width: "35px", height: "35px" }}>
        {DOT_ICONS[name].map(([cx, cy], i) => <circle key={i} cx={cx} cy={cy} r="1.6" />)}
      </svg>);

  }
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none"
    stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round"
    style={{ display: 'block', flexShrink: 0, ...style, height: "35px", width: "40px" }}>
      <path d={STROKE_ICONS[name] || ''} />
    </svg>);

}

Object.assign(window, { Icon, CAT_ICON_KEYS, STROKE_ICONS });