import { cleanText } from '../../util.js';

// A long post is cut off with "Show More"; the full body is at this path (JSON { update: html }).
export const SHOW_MORE_RE = /^\/update_post\/(\d{1,20})\/show_more\/[0-9a-f]{1,64}$/;

// Course updates feed: /course/ID/feed?page=N returns JSON { output: html }.
// Each post: { id, author, date, time, more, bodyEl }. `date` is the footer's
// own text (a link preview inside the body also uses .small.gray, so never
// take the first one); `time` is the post's timestamp as ISO; `more` the
// show-more path when the body is cut off, else null.
export function parseFeedPage(text) {
  let html = text;
  try { html = JSON.parse(text).output || ''; } catch { /* plain HTML */ }
  const doc = new DOMParser().parseFromString(html, 'text/html');
  return [...doc.querySelectorAll('li')]
    .filter((li) => li.querySelector('.update-body, .update-sentence-inner'))
    .map((li) => {
      const moreA = li.querySelector('a.show-more-link[href]');
      let more = null;
      try { more = moreA ? new URL(moreA.getAttribute('href'), 'https://x.schoology.com').pathname : null; } catch { /* bad href */ }
      if (more && !SHOW_MORE_RE.test(more)) more = null;
      const idEl = li.querySelector('[id^="comments-post-"], [id*="-immersive-reader-nid-"]');
      const id = (more && more.match(SHOW_MORE_RE)[1]) || (idEl?.id.match(/(?:post-|nid-)(\d{1,20})/) || [])[1] || null;
      const ts = Number(li.getAttribute('timestamp'));
      const dateEl = li.querySelector('.edge-footer .created') || [...li.querySelectorAll('.small.gray')].find((e) => !e.closest('.update-body'));
      return {
        id,
        author: cleanText(li.querySelector('.update-sentence-inner a, .edge-sentence a')),
        date: cleanText(dateEl),
        time: ts > 0 ? new Date(ts * 1000).toISOString() : null,
        more,
        bodyEl: li.querySelector('.update-body'),
      };
    });
}
