// The one Dewey window that isn't the overlay: Chrome refuses the folder
// picker on a Schoology page and in a frame on one, so the overlay's
// "Choose folder…" opens this small window (background.js, archiveFolderPick).
// It picks (or re-allows) the folder, stamps FOLDER_STAMP so every open card
// redraws, and closes itself.

import { getSavedFolder, pickFolder, ensureWritable } from '../outputs/archive/folder.js';

const FOLDER_STAMP = 'archiveFolderAt'; // overlays/dialog.js
const $ = (id) => document.getElementById(id);

const stamp = () => chrome.storage.local.set({ [FOLDER_STAMP]: Date.now() });

let saved = null;

async function draw() {
  saved = await getSavedFolder().catch(() => null);
  if (!saved) return;
  const granted = (await saved.queryPermission({ mode: 'readwrite' }).catch(() => 'denied')) === 'granted';
  $('current').textContent = saved.name;
  $('choose').textContent = granted ? 'Choose another folder…' : `Allow “${saved.name}” again`;
  if (!granted) {
    const other = document.createElement('button');
    other.className = 'link';
    other.textContent = 'Pick a different one';
    other.addEventListener('click', () => choose(true));
    $('cancel').before(other);
  }
}

async function choose(fresh = false) {
  try {
    const granted = saved && (await saved.queryPermission({ mode: 'readwrite' }).catch(() => 'denied')) === 'granted';
    if (saved && !granted && !fresh) {
      if (!(await ensureWritable(saved))) return;
    } else {
      await pickFolder();
    }
    await stamp();
    window.close();
  } catch (e) {
    if (e?.name === 'AbortError') return; // picker cancelled
    $('hint').textContent = 'Chrome didn’t allow that folder. Try another, or keep Downloads.';
    $('hint').classList.add('bad');
  }
}

$('choose').addEventListener('click', () => choose());
$('cancel').addEventListener('click', () => window.close());
draw();
