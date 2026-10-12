// neo-plan, framed inside the overlay's cards (overlays/dialog.js) so a
// Schoology page never sees the token. Two views, from ?view=:
//   settings  the token, the Schoology line, Courses, and Advanced (server,
//             whether Claude's MCP server is connected)
//   connect   the token only (the Welcome card's last step)
// The page reports its height to the card so the frame can size to it.

import { np } from './np.js';
import { createSyncSettings } from './sync-settings.js';

const $ = (id) => document.getElementById(id);
const view = new URLSearchParams(location.search).get('view') === 'connect' ? 'connect' : 'settings';
document.body.classList.add(view);

const syncSettings = createSyncSettings();
const TOKEN_HINT = 'In neo-plan, open Settings and make an extension token.';

function hint(text, bad = false) {
  const h = $('npTokenHint');
  h.textContent = text;
  h.hidden = !text;
  h.classList.toggle('bad', bad);
}

// Advanced › Claude: whether the local MCP bridge (docs/mcp/MCP-BRIDGE.md)
// has any server connected right now. One check per draw, no polling.
async function drawMcp() {
  let r;
  try { r = await chrome.runtime.sendMessage({ type: 'mcpStatus' }); } catch { /* worker restarting */ }
  $('npMcpStatus').textContent = r?.connected ? 'Connected' : 'Not running';
}

async function draw(s) {
  s = s || (await np('settings')).data || {};
  const sel = $('npServer');
  sel.replaceChildren(...(s.servers || []).map((url) => {
    const o = document.createElement('option');
    o.value = url;
    o.textContent = url.replace(/^https?:\/\//, '');
    o.selected = url === s.server;
    return o;
  }));
  $('npTokenSet').hidden = !s.hasToken;
  $('npTokenNew').hidden = !!s.hasToken;
  $('npTokenMasked').textContent = s.masked || '';
  hint(s.hasToken ? '' : TOKEN_HINT);
  if (view === 'settings') await syncSettings.draw({ hasToken: !!s.hasToken });
}

async function saveToken() {
  const token = $('npTokenInput').value.trim();
  if (!token) { $('npTokenInput').focus(); return; }
  const r = await np('setToken', { token });
  if (!r.ok) {
    // Only a bad format is the token's fault. No reply at all means the service
    // worker is still the old one: Chrome swaps it only on a reload.
    hint(r.data?.error === 'token_format' ? 'A token starts with np_' : 'Reload the extension', true);
    $('npTokenInput').focus();
    return;
  }
  $('npTokenInput').value = '';
  await draw(r.data);
  $('npTokenRemove').focus();
}

async function removeToken() {
  const r = await np('removeToken');
  await draw(r.data);
  $('npTokenInput').focus();
}

function wire() {
  $('npServer').addEventListener('change', async (e) => { await draw((await np('setServer', { server: e.target.value })).data); });
  $('npTokenSave').addEventListener('click', saveToken);
  $('npTokenInput').addEventListener('keydown', (e) => { if (e.key === 'Enter') saveToken(); });
  $('npTokenRemove').addEventListener('click', removeToken);
}

// ── Reporting height to the card that frames this page ──────────────────
function watchHeight() {
  if (typeof ResizeObserver === 'undefined' || parent === window) return;
  let last = -1;
  const report = () => {
    const h = Math.ceil(document.documentElement.getBoundingClientRect().height);
    if (h === last) return;
    last = h;
    parent.postMessage({ type: 'np-frame-height', height: h }, '*');
  };
  new ResizeObserver(report).observe(document.documentElement);
  report();
}

(async function init() {
  wire();
  watchHeight();
  await draw();
  if (view === 'settings') await drawMcp();
})();
