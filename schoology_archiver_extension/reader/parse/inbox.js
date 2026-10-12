// The Inbox pages (iOS roadmap R2): the messages list, one message thread and
// /home/notifications, as plain JSON. Checked against live pages 2026-10-11
// (docs/ios/FINDINGS.md). The DOM parts need a global DOMParser; the string
// helpers at the top are pure (test/inbox.test.js).

import { cleanText } from '../../util.js';
import { htmlToMd, resolveUrl } from '../md.js';
import { parseDueText } from './sync.js';

const squash = (s) => String(s ?? '').replace(/\s+/g, ' ').trim();
const MONTHS = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october', 'november', 'december'];

/** "[AP Comp Sci A - 2350: McLeodT p2 T1] Lesson Slides" → { group, title }. group is null without a bracket. */
export function splitSubject(subject) {
  const s = squash(subject);
  const m = s.match(/^\[([^\]]+)\]\s*(.*)$/);
  return m ? { group: m[1].trim(), title: m[2] || m[1].trim() } : { group: null, title: s };
}

/** "Saturday, October 10, 2026" → { y, m (0-11), d }, or null. */
export function parseDayHeader(text) {
  const m = squash(text).toLowerCase().match(/([a-z]+)\s+(\d{1,2}),?\s+(\d{4})/);
  if (!m || !MONTHS.includes(m[1])) return null;
  return { y: Number(m[3]), m: MONTHS.indexOf(m[1]), d: Number(m[2]) };
}

/** A day ({ y, m, d }) + the "h:mm am" in `text` → ISO string (local time), or null. */
export function atTimeOfDay(day, text) {
  if (!day) return null;
  const t = squash(text).match(/(\d{1,2}):(\d{2})\s*([ap])\.?m\.?/i);
  let h = 0;
  let min = 0;
  if (t) {
    h = Number(t[1]) % 12 + (t[3].toLowerCase() === 'p' ? 12 : 0);
    min = Number(t[2]);
  }
  return new Date(day.y, day.m, day.d, h, min).toISOString();
}

/** Notification type from its `s-edge-type-*` class: material, grade, or the raw suffix. */
export function edgeType(className) {
  const m = String(className || '').match(/s-edge-type-([a-z0-9-]+)/);
  if (!m) return 'other';
  if (m[1] === 'course-materials-add') return 'material';
  if (m[1] === 'grade-add') return 'grade';
  return m[1];
}

/** The numeric id after `prefix` in a path ("/messages/view/123" → "123"), or null. */
export function idAfter(href, prefix) {
  const m = String(href || '').match(new RegExp(`${prefix}(\\d{1,20})(?:[/?#]|$)`));
  return m ? m[1] : null;
}

const pathOf = (a) => {
  const href = a?.getAttribute('href') || '';
  try { return new URL(href, 'https://x.schoology.com').pathname; } catch { return href; }
};

// ── Messages ─────────────────────────────────────────────────────────────

/**
 * /messages?page=N → { threads: [{ id, subject, group, preview, from, fromId,
 * date, unread }], unreadCount, hasMore }. Unread rows: Schoology marks the
 * row (class `unread`/`privatemsg-unread`, or a `.new` marker); not yet seen
 * on a live unread row.
 */
export function parseMessageList(doc) {
  const rows = [...doc.querySelectorAll('table.privatemsg-list tbody tr')].filter((tr) => tr.querySelector('.subject-link'));
  const threads = rows.map((tr) => {
    const link = tr.querySelector('.subject-link');
    const { group, title } = splitSubject(cleanText(link));
    const who = tr.querySelector('.names-date a[href*="/user/"]');
    const date = cleanText(tr.querySelector('.names-date .small.gray, .names-date .gray'));
    return {
      id: idAfter(link.getAttribute('href'), '/messages/view/'),
      subject: title,
      group,
      preview: cleanText(tr.querySelector('.privatemsg-list-body')),
      from: cleanText(who),
      fromId: idAfter(who?.getAttribute('href'), '/user/'),
      date: parseDueText(date) || null,
      unread: /(^|\s)(privatemsg-)?unread(\s|$)/.test(tr.className) || !!tr.querySelector('.new, .marker-new, .privatemsg-unread'),
    };
  }).filter((t) => t.id);
  const count = Number(squash(doc.querySelector('.unread-count')?.textContent));
  return { threads, unreadCount: Number.isFinite(count) ? count : null, hasMore: !!doc.querySelector('.pager-next a') };
}

/**
 * /messages/view/{id} → { subject, group, participants, messages: [{ author,
 * authorId, date, body_md, attachments: [{ title, url }] }] }. Opening this
 * page marks the thread read on Schoology.
 */
export function parseMessage(doc, origin = '') {
  const { group, title } = splitSubject(cleanText(doc.querySelector('.message-title h2')));
  const participants = [...doc.querySelectorAll('.message-participants a')]
    .filter((a) => /\/user\/\d/.test(a.getAttribute('href') || '') && cleanText(a) !== 'You')
    .map((a) => cleanText(a));
  const messages = [...doc.querySelectorAll('.s_message_box')].map((box) => {
    const body = box.querySelector('.message-body');
    const who = body?.querySelector('.name a');
    const date = cleanText(body?.querySelector('.name .small.gray'));
    const attachments = [...(body?.querySelectorAll('.s-message-attachments a[href]') || [])]
      .map((a) => ({ title: cleanText(a), url: resolveUrl(a.getAttribute('href'), origin) }))
      .filter((f) => f.title && f.url);
    let body_md = '';
    if (body) {
      const c = body.cloneNode(true);
      c.querySelectorAll('.name, .s-message-attachments-container, .s-message-attachments, script').forEach((n) => n.remove());
      body_md = htmlToMd(c, origin);
    }
    return { author: cleanText(who), authorId: idAfter(who?.getAttribute('href'), '/user/'), date: parseDueText(date) || null, body_md, attachments };
  });
  return { subject: title, group, participants, messages };
}

// ── Notifications ────────────────────────────────────────────────────────

const ITEM_KINDS = [
  ['assignment', /^\/assignment\/\d/],
  ['assessment', /\/assessments?\//],
  ['page', /^\/page\/\d/],
  ['discussion', /^\/discussion\/\d/],
  ['event', /^\/event\/\d/],
  ['material', /\/materials\//],
];
const itemKind = (path) => (ITEM_KINDS.find(([, re]) => re.test(path)) || ['other'])[0];

/**
 * /home/notifications?page=N → { items: [{ key, type, course, sectionId,
 * text, date, items: [{ title, url, kind }] }], hasMore }. `type` is
 * material, grade, or Schoology's own edge type. The date comes from the day
 * header above each group plus the item's own time.
 */
export function parseNotifications(doc, origin = '') {
  const list = doc.querySelector('ul.s-notifications-mini');
  const items = [];
  let day = null;
  for (const li of list ? [...list.children] : []) {
    if (li.classList.contains('notif-date-header')) { day = parseDayHeader(li.textContent); continue; }
    const edge = li.querySelector('[class*="s-edge-type-"]');
    const sentence = li.querySelector('.edge-sentence');
    if (!edge || !sentence) continue;
    const type = edgeType(edge.className);
    const courseA = sentence.querySelector('a[href*="/course/"]');
    const seen = new Set();
    const linked = [];
    for (const a of [...sentence.querySelectorAll('a[href]'), ...li.querySelectorAll('.other-items-list .added-item a[href]')]) {
      const path = pathOf(a);
      if (a === courseA || /^\/user\//.test(path) || seen.has(path)) continue;
      seen.add(path);
      linked.push({ title: cleanText(a), url: resolveUrl(a.getAttribute('href'), origin), kind: itemKind(path) });
    }
    const timeEl = sentence.querySelector('.edge-time');
    const s = sentence.cloneNode(true);
    s.querySelectorAll('.edge-time, .visually-hidden').forEach((n) => n.remove());
    const date = atTimeOfDay(day, timeEl?.textContent || '');
    items.push({
      key: [type, date || '', linked.map((i) => i.url).join(',')].join('|'),
      type,
      course: cleanText(courseA) || null,
      sectionId: idAfter(pathOf(courseA), '/course/'),
      text: cleanText(s),
      date,
      items: linked,
    });
  }
  const more = [...doc.querySelectorAll('a[href]')].some((a) => /\/home\/notifications\?(.*&)?page=\d/.test(a.getAttribute('href') || ''));
  return { items, hasMore: more };
}
