document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('uploadForm');
  if (!form) return; // pas sur cette page

  // Empêche une double initialisation si le script est chargé deux fois
  if (form.dataset.bound === '1') return;
  form.dataset.bound = '1';

  const dropzone = document.getElementById('dropzone');
  const input = document.getElementById('fileInput');
  const selected = [];

  const syncInput = () => {
    const dt = new DataTransfer();
    selected.forEach(f => dt.items.add(f));
    input.files = dt.files;
  };

  const ensurePreview = () => {
    let preview = dropzone.querySelector('.preview');
    if (!preview) {
      preview = document.createElement('div');
      preview.className = 'preview';
      dropzone.appendChild(preview);
    }
    return preview;
  };

  const render = () => {
    const preview = ensurePreview();
    preview.innerHTML = '';
    selected.forEach((file, idx) => {
      const url = URL.createObjectURL(file);
      const item = document.createElement('div');
      item.className = 'thumb';
      item.style.backgroundImage = `url('${url}')`;

      const remove = document.createElement('button');
      remove.type = 'button';
      remove.textContent = '×';
      remove.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation(); // évite d’ouvrir l’explorateur via la dropzone
        URL.revokeObjectURL(url);
        selected.splice(idx, 1);
        syncInput();
        render();
      });

      item.appendChild(remove);
      preview.appendChild(item);
    });
  };

  const addFiles = list => {
    [...(list || [])].forEach(f => {
      if (!f.type?.startsWith?.('image/')) return;
      if (selected.some(s => s.name === f.name && s.size === f.size)) return;
      selected.push(f);
    });
    syncInput();
    render();
  };

  // Ouvre l’explorateur seulement si on clique le fond de la dropzone (pas ses enfants)
  dropzone.addEventListener('click', (e) => {
    if (e.currentTarget !== e.target) return; // ignore clics sur .preview/.thumb/.button
    input.click();
  }, { passive: true });

  // DnD
  dropzone.addEventListener('dragover', e => { e.preventDefault(); dropzone.classList.add('hover'); });
  dropzone.addEventListener('dragleave', () => dropzone.classList.remove('hover'));
  dropzone.addEventListener('drop', e => {
    e.preventDefault();
    dropzone.classList.remove('hover');
    addFiles(e.dataTransfer.files);
  });

  // Choix via l’explorateur
  input.addEventListener('change', e => addFiles(e.target.files));
});
