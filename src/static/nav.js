// Toggle navbar + horloge
(function () {
  const btn = document.querySelector('.nav-toggle');
  const backdrop = document.getElementById('sidenav-backdrop');
  const sidenav = document.getElementById('sidenav');

  const open = () => {
    document.body.classList.add('sidenav-open');
    btn?.classList.add('open');
    btn?.setAttribute('aria-expanded', 'true');
  };
  const close = () => {
    document.body.classList.remove('sidenav-open');
    btn?.classList.remove('open');
    btn?.setAttribute('aria-expanded', 'false');
  };
  const toggle = () => (document.body.classList.contains('sidenav-open') ? close() : open());

  btn?.addEventListener('click', toggle);
  backdrop?.addEventListener('click', close);
  window.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });

  // Fermer au clic sur un lien
  sidenav?.addEventListener('click', (e) => {
    const a = e.target.closest('a');
    if (a) close();
  });

  // Horloge
  const clockEl = document.getElementById('clock');
  const tick = () => {
    if (!clockEl) return;
    const now = new Date();
    clockEl.textContent = now.toLocaleTimeString('fr-FR', { hour12: false });
  };
  tick();
  setInterval(tick, 1000);
})();