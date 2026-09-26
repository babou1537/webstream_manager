/* WebStream Manager — interface. Aucune dépendance. Chaque bloc ne s'active que si ses éléments existent sur la page. */
(function () {
  'use strict';

  var body = document.body;
  var I18N = window.WS_I18N || {};
  /** Texte traduit ; les paramètres {0}, {1}… sont remplacés par les arguments suivants. */
  function t(key) {
    var s = I18N[key];
    if (s == null) s = key;
    for (var i = 1; i < arguments.length; i++) s = s.split('{' + (i - 1) + '}').join(arguments[i]);
    return s;
  }
  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (ch) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
    });
  }
  function num(v) { return (v === '' || v == null) ? null : Number(String(v).replace(',', '.')); }
  function cssEscape(s) { return (window.CSS && CSS.escape) ? CSS.escape(s) : String(s).replace(/[^a-zA-Z0-9_-]/g, '\\$&'); }
  function fmt(n) { return String(Math.round(n * 100) / 100); }
  function icon(name) {
    var paths = {
      copy: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/>',
      trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
      download: '<path d="M12 4v12M7 11l5 5 5-5"/><path d="M4 20h16"/>',
      search: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/>'
    };
    return '<svg class="i" viewBox="0 0 24 24" aria-hidden="true">' + (paths[name] || '') + '</svg>';
  }

  var baseUrl = (body.dataset.baseUrl || location.origin).replace(/\/+$/, '');
  var urlQuery = body.dataset.urlQuery || '';
  function screenUrl(ref) { return baseUrl + '/' + encodeURIComponent(ref) + '.png' + (urlQuery ? '?' + urlQuery : ''); }

  // ------------------------------------------------------------------ notifications

  function toast(message, kind) {
    var box = $('#toasts');
    if (!box) return;
    var el = document.createElement('div');
    el.className = 'toast ' + (kind || 'ok');
    el.textContent = message;
    box.appendChild(el);
    setTimeout(function () {
      el.classList.add('leaving');
      setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 300);
    }, kind === 'error' ? 6000 : 2600);
  }

  function api(path, opts) {
    opts = opts || {};
    var init = { method: opts.method || 'POST', headers: { 'Accept': 'application/json' }, credentials: 'same-origin' };
    if (opts.form) {
      init.headers['Content-Type'] = 'application/x-www-form-urlencoded';
      init.body = new URLSearchParams(opts.form).toString();
    } else if (opts.json) {
      init.headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(opts.json);
    }
    return fetch(path, init).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (data) {
        if (!res.ok) {
          var err = new Error(data.error || ('HTTP ' + res.status));
          err.code = data.error;
          throw err;
        }
        return data;
      });
    });
  }
  function failToast(e) { toast(e.code && I18N['err.' + e.code] ? t('err.' + e.code) : t('js.generic'), 'error'); }

  function copyText(text, button) {
    function done() { toast(t('js.copied'), 'ok'); if (button) { button.classList.add('copied'); setTimeout(function () { button.classList.remove('copied'); }, 900); } }
    function fallback() {
      var ta = document.createElement('textarea');
      ta.value = text; ta.style.position = 'fixed'; ta.style.left = '-9999px';
      document.body.appendChild(ta); ta.select();
      try { document.execCommand('copy'); done(); } catch (e) { toast(t('js.copyFail', text), 'error'); }
      document.body.removeChild(ta);
    }
    if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(text).then(done, fallback);
    else fallback();
  }

  // ------------------------------------------------------------------ barre latérale, horloge, profils

  (function () {
    var toggle = $('#navToggle'), backdrop = $('#backdrop');
    function set(open) {
      body.classList.toggle('sidenav-open', open);
      if (toggle) toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    }
    if (toggle) toggle.addEventListener('click', function () { set(!body.classList.contains('sidenav-open')); });
    if (backdrop) backdrop.addEventListener('click', function () { set(false); });

    var clock = $('#clock');
    if (clock) {
      var tick = function () { clock.textContent = new Date().toLocaleTimeString(document.documentElement.lang || undefined, { hour12: false }); };
      tick(); setInterval(tick, 1000);
    }

    var sw = $('#profileSwitch');
    if (sw) {
      var btn = $('.ps-current', sw), menu = $('.ps-menu', sw);
      var close = function () { menu.hidden = true; btn.setAttribute('aria-expanded', 'false'); };
      btn.addEventListener('click', function (e) {
        e.stopPropagation();
        menu.hidden = !menu.hidden;
        btn.setAttribute('aria-expanded', menu.hidden ? 'false' : 'true');
      });
      document.addEventListener('click', function (e) { if (!sw.contains(e.target)) close(); });
      $$('.ps-item', sw).forEach(function (item) {
        item.addEventListener('click', function () {
          if (item.classList.contains('active')) { close(); return; }
          api('/profiles/' + encodeURIComponent(item.dataset.profileId) + '/activate', { form: { bind: '1' } })
            .then(function () { location.reload(); }, failToast);
        });
      });
    }
  })();

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      body.classList.remove('sidenav-open');
      var menu = $('#profileSwitch .ps-menu');
      if (menu) menu.hidden = true;
    }
  });

  // copier (délégué) et confirmations
  document.addEventListener('click', function (e) {
    var copy = e.target.closest && e.target.closest('[data-copy]');
    if (copy) { e.preventDefault(); copyText(copy.dataset.copy, copy); return; }
    var cond = e.target.closest && e.target.closest('[data-confirm-if]');
    if (cond) {
      var parts = cond.dataset.confirmIf.split('=');
      var field = cond.form && cond.form.elements[parts[0]];
      if (field && field.value === parts[1] && !window.confirm(cond.dataset.confirmText || t('js.confirm'))) e.preventDefault();
    }
  });
  document.addEventListener('submit', function (e) {
    var f = e.target;
    if (f.dataset && f.dataset.confirm && !window.confirm(f.dataset.confirm)) e.preventDefault();
  });

  // ------------------------------------------------------------------ fenêtre modale

  var modal = null;
  function openModal(id, titleHtml, bodyHtml, single) {
    closeModal();
    modal = document.getElementById(id);
    if (!modal) return null;
    $('.modal-title', modal).innerHTML = titleHtml;
    var b = $('.modal-body', modal);
    b.className = 'modal-body' + (single ? ' single' : '');
    b.innerHTML = bodyHtml;
    modal.classList.add('open');
    body.classList.add('modal-open');
    return modal;
  }
  function closeModal() {
    if (!modal) return;
    modal.classList.remove('open');
    body.classList.remove('modal-open');
    var m = modal; modal = null;
    if (m.dataset.reloadOnClose === '1') location.reload();
  }
  document.addEventListener('click', function (e) {
    if (modal && e.target.closest && e.target.closest('[data-close]') && modal.contains(e.target)) closeModal();
  });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeModal(); });

  // ------------------------------------------------------------------ bibliothèque

  (function () {
    var grid = $('#library-grid');
    if (!grid) return;

    // filtre + taille des vignettes
    var search = $('#searchInput');
    function applyFilter() {
      var q = (search.value || '').trim().toLowerCase();
      $$('.library-item', grid).forEach(function (it) { it.classList.toggle('hidden', q && it.dataset.filename.indexOf(q) === -1); });
    }
    if (search) search.addEventListener('input', applyFilter);
    var seg = $('#sizeSeg');
    function setSize(size) {
      grid.className = 'grid size-' + size;
      $$('button', seg).forEach(function (b) { b.classList.toggle('active', b.dataset.size === size); });
      try { localStorage.setItem('ws.libSize', size); } catch (e) { /* stockage indisponible */ }
    }
    if (seg) {
      $$('button', seg).forEach(function (b) { b.addEventListener('click', function () { setSize(b.dataset.size); }); });
      var saved = null; try { saved = localStorage.getItem('ws.libSize'); } catch (e) { /* ignoré */ }
      if (saved === 'small' || saved === 'medium' || saved === 'large') setSize(saved);
    }

    // envoi de fichiers
    var input = $('#fileInput'), zone = $('#dropzone'), prog = $('#uploadProgress');
    function upload(files) {
      var list = Array.prototype.filter.call(files, function (f) { return /\.(png|jpe?g|gif|webp|bmp|tiff?|svg)$/i.test(f.name); });
      if (!list.length) { toast(t('js.noValidImage'), 'error'); return; }
      var fd = new FormData();
      list.forEach(function (f) { fd.append('files', f, f.name); });
      var xhr = new XMLHttpRequest();
      xhr.open('POST', '/library/upload');
      xhr.setRequestHeader('Accept', 'application/json');
      prog.hidden = false;
      var bar = $('i', prog); bar.style.width = '0';
      xhr.upload.onprogress = function (ev) { if (ev.lengthComputable) bar.style.width = Math.round(ev.loaded / ev.total * 100) + '%'; };
      xhr.onload = function () {
        var data = {}; try { data = JSON.parse(xhr.responseText); } catch (e) { /* ignoré */ }
        prog.hidden = true;
        if (xhr.status === 200) { toast(t('js.uploaded', data.stored || list.length), 'ok'); setTimeout(function () { location.reload(); }, 500); }
        else if (data.rejected && data.rejected.length) {
          toast(t('js.rejected', data.rejected.join(', ')), 'error');
          if (data.stored) setTimeout(function () { location.reload(); }, 1500);
        } else toast(xhr.status === 413 ? t('js.tooLarge') : t('js.uploadFail'), 'error');
      };
      xhr.onerror = function () { prog.hidden = true; toast(t('js.uploadInterrupted'), 'error'); };
      xhr.send(fd);
    }
    var pick = function () { input.click(); };
    var upBtn = $('#uploadBtn');
    if (upBtn) upBtn.addEventListener('click', pick);
    zone.addEventListener('click', pick);
    zone.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick(); } });
    input.addEventListener('change', function () { if (input.files.length) upload(input.files); });
    ['dragenter', 'dragover'].forEach(function (n) { window.addEventListener(n, function (e) { e.preventDefault(); zone.classList.add('over'); }); });
    window.addEventListener('dragleave', function (e) { if (!e.relatedTarget) zone.classList.remove('over'); });
    window.addEventListener('drop', function (e) {
      e.preventDefault(); zone.classList.remove('over');
      if (e.dataTransfer && e.dataTransfer.files.length) upload(e.dataTransfer.files);
    });

    // aperçu d'une image
    function openImage(item) {
      var file = item.dataset.file, used = [];
      try { used = JSON.parse(item.dataset.usage || '[]'); } catch (e) { /* ignoré */ }
      var title = '<span class="name">' + esc(file) + '</span><span class="muted">' + esc(item.dataset.size || '') + '</span>';
      var usedHtml = used.length
        ? '<div class="used-list">' + used.map(function (r) { return '<code>' + esc(r) + '</code>'; }).join('') + '</div>'
        : '<p class="muted">' + esc(t('js.notUsed')) + '</p>';
      var html = '<div class="preview-stage"><div class="preview-frame natural"><img alt="' + esc(file) + '" src="/content/' + encodeURIComponent(file) + '" /></div></div>'
        + '<div class="side"><section><h4>' + esc(t('js.usedBy')) + '</h4>' + usedHtml + '</section>'
        + '<section><h4>' + esc(t('js.file')) + '</h4><div class="form-row"><a class="btn btn-sm" href="/content/' + encodeURIComponent(file) + '" download="' + esc(file) + '">' + icon('download') + esc(t('js.download')) + '</a></div></section>'
        + '<div class="danger-zone"><button type="button" class="btn btn-danger" id="delImage">' + icon('trash') + esc(t('js.deleteFromLib')) + '</button></div></div>';
      var m = openModal('libraryModal', title, html, false);
      $('#delImage', m).addEventListener('click', function () {
        if (!window.confirm(t('js.confirmDeleteImage', file))) return;
        api('/library/delete', { json: { file: file } }).then(function () {
          item.parentNode.removeChild(item); closeModal(); toast(t('js.imageDeleted'), 'ok');
        }, failToast);
      });
    }
    grid.addEventListener('click', function (e) { var it = e.target.closest('.library-item'); if (it) openImage(it); });
    grid.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { var it = e.target.closest('.library-item'); if (it) openImage(it); }
    });
  })();

  // ------------------------------------------------------------------ écrans

  (function () {
    var data = $('#screens-data');
    if (!data) return;
    var families = [], library = [];
    try { families = JSON.parse(data.dataset.families || '[]'); } catch (e) { /* ignoré */ }
    try { library = JSON.parse(data.dataset.library || '[]'); } catch (e) { /* ignoré */ }

    // filtre
    var filter = $('#screenFilter');
    if (filter) filter.addEventListener('input', function () {
      var q = filter.value.trim().toLowerCase();
      $$('.screen-card').forEach(function (c) { c.classList.toggle('hidden', q && c.dataset.search.indexOf(q) === -1); });
      $$('.family-group').forEach(function (g) { g.style.display = $$('.screen-card:not(.hidden)', g).length || !q ? '' : 'none'; });
    });

    function famOptions(selected) {
      return '<option value="">' + esc(t('js.none')) + '</option>' + families.map(function (f) {
        return '<option value="' + f.id + '"' + (String(f.id) === String(selected || '') ? ' selected' : '') + '>' + esc(f.name) + '</option>';
      }).join('');
    }
    function placeholder(w, h) { return '/screens/placeholder/' + Math.round((w || 16) * 100) + '/' + Math.round((h || 9) * 100) + '.svg'; }

    // ---- création
    var add = $('#addScreenBtn');
    if (add) add.addEventListener('click', function () {
      var html = '<div class="side" style="grid-column:1/-1"><form class="side" method="post" action="/screens/create">'
        + '<label class="field">' + esc(t('js.screenName')) + '<input name="ref" required maxlength="64" placeholder="metro-station-1" autofocus />'
        + '<span class="hint">' + esc(t('js.screenNameHint')) + '</span></label>'
        + '<label class="field">' + esc(t('js.family')) + '<select name="familyId">' + famOptions('') + '</select></label>'
        + '<div class="form-grid"><label class="field">' + esc(t('js.width')) + '<input name="width" type="number" step="0.1" min="0.1" placeholder="16" /></label>'
        + '<label class="field">' + esc(t('js.height')) + '<input name="height" type="number" step="0.1" min="0.1" placeholder="9" /></label></div>'
        + '<p class="hint">' + esc(t('js.resolutionHint')) + '</p>'
        + '<div class="form-row"><button class="btn btn-primary" type="submit">' + esc(t('js.createScreen')) + '</button><button class="btn" type="button" data-close>' + esc(t('js.cancel')) + '</button></div></form></div>';
      var m = openModal('screenModal', '<span class="name">' + esc(t('js.newScreen')) + '</span>', html, true);
      var first = $('input[name=ref]', m); if (first) first.focus();
    });

    // ---- édition
    function openScreen(tile) {
      var ref = tile.dataset.ref;
      var st = { content: tile.dataset.content || '', width: tile.dataset.width, height: tile.dataset.height, familyId: tile.dataset.familyId };
      var url = screenUrl(ref);
      var card = tile.closest('.screen-card');

      var title = '<span class="name"><code>' + esc(ref) + '</code></span>'
        + '<div class="url-row"><div class="copybox" style="flex:1;min-width:0"><code>' + esc(url) + '</code>'
        + '<button type="button" class="btn btn-sm" data-copy="' + esc(url) + '">' + icon('copy') + esc(t('js.copy')) + '</button></div></div>';

      var html = '<div><div class="preview-stage"><div class="preview-frame" id="pvFrame" style="--w:' + (st.width || 16) + ';--h:' + (st.height || 9) + '">'
        + '<img id="pvImg" alt="" src="' + (st.content ? '/content/' + encodeURIComponent(st.content) : placeholder(num(st.width), num(st.height))) + '" /></div></div>'
        + '<p class="stage-note" id="pvNote"></p>'
        + '<div class="form-row" style="justify-content:center;margin-top:.4rem"><button type="button" class="btn btn-sm btn-ghost" id="pvToggle">' + esc(t('js.viewOriginal')) + '</button></div></div>'
        + '<div class="side">'
        + '<section><h4>' + esc(t('js.imageShown')) + '</h4><div class="searchbox"><input type="search" id="pickSearch" placeholder="' + esc(t('js.searchImage')) + '" autocomplete="off" />' + icon('search') + '</div>'
        + '<div class="picker" id="picker">' + library.map(function (f) {
          return '<button type="button" class="pick' + (f === st.content ? ' current' : '') + '" data-file="' + esc(f) + '" title="' + esc(f) + '">'
            + '<img loading="lazy" decoding="async" alt="' + esc(f) + '" src="/thumb/' + encodeURIComponent(f) + '?w=240" /></button>';
        }).join('') + (library.length ? '' : '<p class="muted">' + esc(t('js.libraryEmpty')) + '</p>') + '</div>'
        + '<div class="form-row"><span class="muted" id="curFile"></span><button type="button" class="btn btn-sm" id="unassign">' + esc(t('js.removeImage')) + '</button></div></section>'
        + '<section><h4>' + esc(t('js.family')) + '</h4><select id="famSel">' + famOptions(st.familyId) + '</select></section>'
        + '<section><h4>' + esc(t('js.resolution')) + '</h4><div class="dim-row"><input id="dimW" type="number" step="0.1" min="0.1" value="' + esc(st.width || '') + '" placeholder="' + esc(t('js.widthShort')) + '" aria-label="' + esc(t('js.widthAria')) + '" />'
        + '<span>×</span><input id="dimH" type="number" step="0.1" min="0.1" value="' + esc(st.height || '') + '" placeholder="' + esc(t('js.heightShort')) + '" aria-label="' + esc(t('js.heightAria')) + '" />'
        + '<button type="button" class="btn btn-sm btn-primary" id="dimSave">' + esc(t('js.save')) + '</button></div></section>'
        + '<div class="danger-zone"><button type="button" class="btn btn-danger" id="delScreen">' + icon('trash') + esc(t('js.deleteScreen')) + '</button></div></div>';

      var m = openModal('screenModal', title, html, false);
      var frame = $('#pvFrame', m), img = $('#pvImg', m), note = $('#pvNote', m), cur = $('#curFile', m);

      function refreshView() {
        frame.style.setProperty('--w', st.width || 16);
        frame.style.setProperty('--h', st.height || 9);
        img.src = st.content ? '/content/' + encodeURIComponent(st.content) : placeholder(num(st.width), num(st.height));
        cur.textContent = st.content || t('js.noImage');
        $('#unassign', m).disabled = !st.content;
        note.textContent = t('js.stageNote', st.width || '—', st.height || '—');
        $$('.pick', m).forEach(function (p) { p.classList.toggle('current', p.dataset.file === st.content); });
      }
      function refreshTile() {
        tile.dataset.content = st.content; tile.dataset.width = st.width || ''; tile.dataset.height = st.height || ''; tile.dataset.familyId = st.familyId || '';
        var f = $('.sc-frame', tile);
        f.style.setProperty('--w', st.width || 16); f.style.setProperty('--h', st.height || 9);
        $('img', f).src = st.content ? '/thumb/' + encodeURIComponent(st.content) : placeholder(num(st.width), num(st.height));
        var sub = $('.sc-sub', tile); if (sub) sub.textContent = (st.width || '—') + ' × ' + (st.height || '—');
        var status = $('.tile-status', card);
        status.className = 'tile-status chip ' + (st.content ? 'chip-ok' : 'chip-warn');
        status.textContent = st.content ? t('js.statusImage') : t('js.statusNone');
      }
      refreshView();

      $('#pvToggle', m).addEventListener('click', function () {
        var natural = frame.classList.toggle('natural');
        this.textContent = natural ? t('js.viewScreen') : t('js.viewOriginal');
        note.hidden = natural;
      });

      $('#picker', m).addEventListener('click', function (e) {
        var p = e.target.closest('.pick'); if (!p) return;
        api('/screens/' + encodeURIComponent(ref) + '/assign', { form: { file: p.dataset.file } }).then(function () {
          st.content = p.dataset.file; refreshView(); refreshTile(); toast(t('js.imageAssigned'), 'ok');
        }, failToast);
      });
      $('#pickSearch', m).addEventListener('input', function () {
        var q = this.value.trim().toLowerCase();
        $$('.pick', m).forEach(function (p) { p.classList.toggle('hidden', q && p.dataset.file.toLowerCase().indexOf(q) === -1); });
      });
      $('#unassign', m).addEventListener('click', function () {
        api('/screens/' + encodeURIComponent(ref) + '/unassign').then(function () {
          st.content = ''; refreshView(); refreshTile(); toast(t('js.imageRemoved'), 'ok');
        }, failToast);
      });
      $('#famSel', m).addEventListener('change', function () {
        var v = this.value;
        api('/screens/' + encodeURIComponent(ref) + '/family', { form: { familyId: v } }).then(function () {
          st.familyId = v; refreshTile(); modal.dataset.reloadOnClose = '1'; toast(t('js.familyChanged'), 'ok');
        }, failToast);
      });
      $('#dimSave', m).addEventListener('click', function () {
        var w = num($('#dimW', m).value), h = num($('#dimH', m).value);
        if (!(w > 0) || !(h > 0)) { toast(t('js.dimsInvalid'), 'error'); return; }
        api('/screens/' + encodeURIComponent(ref) + '/dimensions', { form: { width: fmt(w), height: fmt(h) } }).then(function () {
          st.width = fmt(w); st.height = fmt(h); refreshView(); refreshTile(); toast(t('js.dimsSaved'), 'ok');
        }, failToast);
      });
      $('#delScreen', m).addEventListener('click', function () {
        if (!window.confirm(t('js.confirmDeleteScreen', ref))) return;
        api('/screens/' + encodeURIComponent(ref) + '/delete').then(function () {
          card.parentNode.removeChild(card); closeModal(); toast(t('js.screenDeleted'), 'ok');
        }, failToast);
      });
    }
    document.addEventListener('click', function (e) {
      var t = e.target.closest && e.target.closest('.screen-tile');
      if (t) openScreen(t);
    });
  })();

  // ------------------------------------------------------------------ réglages et test de connexion

  function runTest(url, out, button) {
    if (button) button.disabled = true;
    out.className = 'test-result muted';
    out.textContent = t('js.testRunning');
    api('/settings/test', { form: { url: url || '' } }).then(function (r) {
      out.className = 'test-result alert ' + (r.ok ? 'alert-ok' : 'alert-error');
      out.innerHTML = '<p style="margin:0"><strong>' + esc(r.ok ? t('js.testOk') : t('js.testFail')) + '</strong> — ' + esc(r.message) + '</p><p class="hint" style="margin:.3rem 0 0"><code>' + esc(r.url) + '</code></p>';
    }, function () { out.className = 'test-result alert alert-error'; out.textContent = t('js.testCantRun'); })
      .then(function () { if (button) button.disabled = false; });
  }

  (function () {
    var form = $('#settingsForm');
    if (form) {
      var pubBox = $('input[name=publicEnabled]', form), when = $('.only-when[data-when=publicEnabled]', form), port = $('#publicPort', form);
      var sync = function () { when.hidden = !pubBox.checked; };
      pubBox.addEventListener('change', sync);
      var mirror = function () { $$('[data-port-mirror]', form).forEach(function (s) { s.textContent = port.value || '8283'; }); };
      port.addEventListener('input', mirror);
      $$('[data-suggest-ip]', form).forEach(function (b) {
        b.addEventListener('click', function () { $('#publicUrl', form).value = 'http://' + b.dataset.suggestIp + ':' + (port.value || '8283'); });
      });
      $('#testBtn', form).addEventListener('click', function () { runTest($('#publicUrl', form).value.trim(), $('#testResult', form), this); });
    }
    var quick = $('#quickTest');
    if (quick) quick.addEventListener('click', function () {
      var out = $('#quickTestResult');
      quick.disabled = true; out.textContent = t('js.testRunning');
      api('/settings/test', { form: { url: '' } }).then(function (r) { out.textContent = (r.ok ? '✔ ' : '✖ ') + r.message; },
        function () { out.textContent = '✖ ' + t('js.testImpossible'); }).then(function () { quick.disabled = false; });
    });

    // redémarrage du serveur web après un changement de port : on attend qu'il réponde puis on rebascule
    var restart = $('#restartNotice');
    if (restart) {
      var target = location.protocol + '//' + location.hostname + ':' + restart.dataset.port;
      var tries = 0;
      var poll = function () {
        tries++;
        fetch(target + '/ping', { mode: 'no-cors' }).then(function () { location.href = target + '/settings'; },
          function () { if (tries < 25) setTimeout(poll, 700); else restart.innerHTML = '<p>' + esc(t('js.restartNoAnswer', target)) + '</p>'; });
      };
      setTimeout(poll, 1200);
    }
  })();
})();
