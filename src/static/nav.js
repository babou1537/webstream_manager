// Toggle navbar + horloge
(function() {
  if (window.__NAV_INIT__) return;
  window.__NAV_INIT__ = true;

  const body = document.body;
  const toggle = document.querySelector('.nav-toggle');
  const sidenav = document.querySelector('.sidenav');
  const backdrop = document.querySelector('.sidenav-backdrop');

  if (!toggle || !sidenav) return;

  function openSidenav() {
    body.classList.add('sidenav-open');
    toggle.setAttribute('aria-expanded', 'true');
  }
  function closeSidenav() {
    body.classList.remove('sidenav-open');
    toggle.setAttribute('aria-expanded', 'false');
  }
  function toggleSidenav() {
    if (body.classList.contains('sidenav-open')) closeSidenav();
    else openSidenav();
  }

  toggle.addEventListener('click', function(e) {
    e.preventDefault();
    toggleSidenav();
  });

  if (backdrop) {
    backdrop.addEventListener('click', closeSidenav);
  }

  // Fermeture avec ESC
  window.addEventListener('keydown', function(e) {
    if (e.key === 'Escape' && body.classList.contains('sidenav-open')) closeSidenav();
  });

  // Exposer si autre code l’utilise
  window.closeSidenav = closeSidenav;

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