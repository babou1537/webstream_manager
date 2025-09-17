(() => {
  // Crée l’overlay une seule fois
  let overlay = document.querySelector('.preview-overlay');
  if (!overlay) {
    overlay = document.createElement('div');
    overlay.className = 'preview-overlay';
    overlay.innerHTML = `
      <div class="preview-backdrop" data-action="close"></div>
      <div class="preview-dialog" role="dialog" aria-modal="true" aria-label="Aperçu">
        <div class="preview-header">
          <div class="preview-title" id="preview-title"></div>
          <div class="preview-actions">
            <button class="preview-delete" aria-label="Supprimer" title="Supprimer" data-action="delete">🗑️</button>
            <button class="preview-close" aria-label="Fermer" title="Fermer" data-action="close">×</button>
          </div>
        </div>
        <div class="preview-body">
          <img class="preview-img" id="preview-img" alt="Aperçu" />
        </div>
      </div>
    `;
    document.body.appendChild(overlay);
  }
  const imgEl = overlay.querySelector('#preview-img');
  const titleEl = overlay.querySelector('#preview-title');
  let currentFile = null;

  const open = (src, title) => {
    imgEl.src = src;
    imgEl.alt = title || 'Aperçu';
    titleEl.textContent = title || '';
    // extraire le nom de fichier depuis /content/<file>
    try {
      const ix = src.lastIndexOf('/content/');
      currentFile = ix >= 0 ? decodeURIComponent(src.substring(ix + 9)) : null;
    } catch { currentFile = null; }
    overlay.classList.add('open');
    document.body.classList.add('body-modal-open');
  };
  const close = () => {
    overlay.classList.remove('open');
    document.body.classList.remove('body-modal-open');
    // petite purge de la source pour libérer mémoire des blobs si besoin
    // (inutile ici car on utilise les URLs /content/…)
  };

  // Délégation: clic sur une image de la grille
  document.addEventListener('click', (e) => {
    const grid = document.getElementById('library-grid');
    if (!grid) return; // pas sur cette page
    const img = e.target.closest('#library-grid img.previewable');
    if (!img) return;
    e.preventDefault();
    open(img.src, img.alt || '');
  });

  // Actions de fermeture
  overlay.addEventListener('click', async (e) => {
    const act = e.target?.dataset?.action;
    if (act === 'close') {
      close();
      return;
    }
    if (act === 'delete') {
      if (!currentFile) return;
      const ok = confirm(`Supprimer le fichier "${currentFile}" de la bibliothèque ?`);
      if (!ok) return;
      try {
        const res = await fetch('/library/delete', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ file: currentFile })
        });
        if (!res.ok) {
          const data = await res.json().catch(() => ({}));
          if (data.error === 'FILE_IN_USE') {
            alert('Ce fichier est assigné à un écran. Déassigner avant de supprimer.');
          } else if (data.error === 'FILE_NOT_FOUND') {
            alert('Fichier introuvable.');
          } else {
            alert('Erreur lors de la suppression.');
          }
          return;
        }
        // retirer la carte de la grille
        const img = document.querySelector(`#library-grid img[data-filename="${CSS.escape(currentFile)}"]`);
        img?.closest('.card')?.remove();
        close();
      } catch (err) {
        console.error(err);
        alert('Erreur réseau.');
      }
    }
  });
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && overlay.classList.contains('open')) {
      close();
    }
  });
})();