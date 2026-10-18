// The done ripple: Dewey's one signature motion (the iOS app's DoneRipple.swift).
// When a To Do row is marked Done (or Studied), two thin accent rings spread
// out from the button's check, like the ripples around the logo's drop. About
// 400 ms, only on marking done (never on undo), and nothing at all with
// prefers-reduced-motion. The rings are our own element, fixed over the
// button and removed when they finish, so the row redrawing under them (the
// button is replaced as soon as the state flips) doesn't cut them short.

export const RIPPLE_MS = 420;

/** The two rings' keyframes: an outer one leads, an inner one follows a beat later. Pure. */
export function rippleRings() {
  return [
    { scale: 2.15, opacity: 0.55, width: 2, delay: 0 },
    { scale: 1.7, opacity: 0.4, width: 1.6, delay: 0.22 },
  ].map((r) => ({
    keyframes: [
      { transform: 'scale(1)', opacity: r.opacity, borderWidth: `${r.width}px` },
      { transform: `scale(${r.scale})`, opacity: 0, borderWidth: `${r.width / 2}px` },
    ],
    timing: { duration: RIPPLE_MS * (1 - r.delay), delay: RIPPLE_MS * r.delay, easing: 'cubic-bezier(.2,.7,.3,1)', fill: 'backwards' },
  }));
}

/**
 * Plays the ripple from `from` (the button just pressed; its svg check when it
 * has one), appended to `layer` (our own row, in our shadow root). → the rings'
 * element, or null when nothing plays (reduced motion, no Web Animations).
 */
export function rippleFrom(from, layer, { win = globalThis.window } = {}) {
  if (!from || !layer || !win || win.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return null;
  const doc = layer.ownerDocument;
  const target = from.querySelector?.('svg') || from;
  const r = target.getBoundingClientRect();
  if (!r.width || typeof doc.createElement('div').animate !== 'function') return null;
  const color = win.getComputedStyle(from).getPropertyValue('--accent').trim() || '#1D9E75';
  const d = Math.max(r.width, r.height) + 4;
  const box = doc.createElement('div');
  box.setAttribute('aria-hidden', 'true');
  Object.assign(box.style, {
    position: 'fixed', left: `${r.left + r.width / 2 - d / 2}px`, top: `${r.top + r.height / 2 - d / 2}px`,
    width: `${d}px`, height: `${d}px`, pointerEvents: 'none', zIndex: '1',
  });
  for (const { keyframes, timing } of rippleRings()) {
    const ring = doc.createElement('div');
    Object.assign(ring.style, {
      position: 'absolute', inset: '0', borderRadius: '50%', border: `2px solid ${color}`, opacity: '0', boxSizing: 'border-box',
    });
    box.append(ring);
    ring.animate(keyframes, timing);
  }
  layer.append(box);
  win.setTimeout(() => box.remove(), RIPPLE_MS + 50);
  return box;
}
