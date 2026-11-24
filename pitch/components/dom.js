/*
 * Focus Mode+ Pitch · DOM and content helpers shared by components and scenes.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch = global.Pitch || {};
  const C = global.PITCH_CONTENT || {};

  const PLACEHOLDER = /^\s*\[[A-Z0-9_]+\]\s*$/;

  /* A content value is resolved when it is a non-empty string that is not a [TOKEN]. */
  function isResolved(value) {
    return typeof value === 'string' && value.trim() !== '' && !PLACEHOLDER.test(value);
  }

  /* Never let a raw [TOKEN] reach the stage: unresolved values become the fallback (default ''). */
  function value(key, fallback) {
    const v = C[key];
    return isResolved(v) ? v : (fallback || '');
  }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  /* Build DOM from a trusted template string (all content values must pass through esc()). */
  function frag(markup) {
    const t = document.createElement('template');
    t.innerHTML = markup.trim();
    return t.content;
  }

  function mount(root, markup) {
    root.appendChild(frag(markup));
    return root;
  }

  function q(root, sel) { return root.querySelector(sel); }
  function qa(root, sel) { return Array.prototype.slice.call(root.querySelectorAll(sel)); }

  /* Every unresolved placeholder in the content file (for the rehearsal warning). */
  function unresolved() {
    const list = [];
    Object.keys(C).forEach(function (k) {
      const v = C[k];
      if (typeof v === 'string' && PLACEHOLDER.test(v)) list.push(k + ' = ' + v.trim());
    });
    return list;
  }

  /* Small inline arrow (Inter's latin subset has no U+2192 glyph). */
  function arrow(cls) {
    return '<svg class="arrow' + (cls ? ' ' + cls : '') + '" viewBox="0 0 28 16" aria-hidden="true" focusable="false"><path d="M2 8h22M17 2l7 6-7 6" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  }

  Pitch.dom = Object.freeze({ esc: esc, frag: frag, mount: mount, q: q, qa: qa, arrow: arrow });
  Pitch.content = Object.freeze({ data: C, isResolved: isResolved, value: value, unresolved: unresolved });
})(window);
