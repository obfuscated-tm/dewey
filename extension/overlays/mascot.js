// Dewey the drop (brand/README.md): a small picture for the few moments he was
// drawn for (the Welcome card says hello, the Archiver card while a run is
// going). Placeholder art: the files in overlays/mascot/ are copies of
// brand/mascot/ made by brand/sync-mascot.sh, so a redraw is a file swap.
//
// Dark themes invert the whole page and re-invert <img> (theme.js), so an
// <img> shows its file's own colours: the -dark file (light outline) there.

import { getSettings, onSettings } from './ui.js';
import { THEMES } from './theme.js';

export const POSES = ['hello', 'caught-up', 'archiving', 'uh-oh'];

/** The file for `pose` ('' outside the extension, where there's no chrome.runtime). */
export function mascotSrc(pose, dark = false) {
  try {
    return chrome.runtime.getURL(`overlays/mascot/dewey-${pose}${dark ? '-dark' : ''}.svg`);
  } catch {
    return '';
  }
}

const isDark = (s) => !!THEMES[s?.theme]?.dark;

/** An <img> of Dewey in `pose`, `size` px square, following the theme. Decorative. */
export function mascot(pose, { size = 56, doc = document } = {}) {
  const img = doc.createElement('img');
  img.className = 'mascot';
  img.alt = '';
  img.setAttribute('aria-hidden', 'true');
  img.width = img.height = size;
  img.draggable = false;
  const draw = (s) => {
    const src = mascotSrc(pose, isDark(s));
    if (src && img.getAttribute('src') !== src) img.src = src;
    img.hidden = !src;
  };
  draw(getSettings());
  onSettings(draw);
  return img;
}
