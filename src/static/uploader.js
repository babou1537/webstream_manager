document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('uploadForm');
  const dz = document.getElementById('dropzone');
  const input = document.getElementById('fileInput');
  if (!form || !dz || !input) return;

  dz.addEventListener('click', () => input.click());

  ['dragenter', 'dragover'].forEach(evt => dz.addEventListener(evt, e => {
    e.preventDefault();
    e.stopPropagation();
    dz.classList.add('drag');
  }));

  ;['dragleave', 'drop'].forEach(evt => dz.addEventListener(evt, e => {
    e.preventDefault();
    e.stopPropagation();
    dz.classList.remove('drag');
  }));

  dz.addEventListener('drop', (e) => {
    const files = e.dataTransfer.files;
    input.files = files;
  });
});
