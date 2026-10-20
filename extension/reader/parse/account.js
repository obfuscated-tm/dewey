// /settings/account: the student's Schoology username. At FUHSD it is the
// 7-digit student number printed (and Code 39 barcoded) on the school ID card
// (checked on the live page 2026-10-20, docs/ios/FINDINGS.md). Pure string
// work, no DOMParser, so it runs under node (test/account.test.js).

const decode = (s) => s
  .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
  .replace(/&quot;/g, '"').replace(/&#0?39;/g, "'");

/**
 * The text in the Username row's `.form-text`, or null when the page has no
 * Username row (another layout, or not the account page).
 */
export function accountUsername(html) {
  const s = String(html ?? '');
  const at = s.search(/id=["']edit-user-username-wrapper["']/);
  if (at < 0) return null;
  // The row ends where the next form item starts.
  const rest = s.slice(at);
  const next = rest.slice(1).search(/class=["']form-item["']/);
  const row = next < 0 ? rest : rest.slice(0, next + 1);
  const m = row.match(/class=["'][^"']*\bform-text\b[^"']*["'][^>]*>([\s\S]*?)<\/div>/);
  if (!m) return null;
  const text = decode(m[1].replace(/<[^>]*>/g, '')).replace(/\s+/g, ' ').trim();
  return text || null;
}

/** A student number: digits only (spaces and dashes dropped), 4–12 long; else null. */
export function studentNumber(username) {
  const d = String(username ?? '').replace(/[\s-]/g, '');
  return /^\d{4,12}$/.test(d) ? d : null;
}

/** `/settings/account` → { username, studentId } (studentId null when the username isn't a number). */
export function parseAccount(html) {
  const username = accountUsername(html);
  return { username, studentId: studentNumber(username) };
}
