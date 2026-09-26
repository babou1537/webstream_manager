(() => {
  'use strict';

  const searchInput = document.getElementById('searchInput');
  const searchResults = document.getElementById('searchResults');
  const clearSearch = document.getElementById('clearSearch');
  const libraryItems = document.querySelectorAll('.library-item');
  const noFilesMsg = document.querySelector('.no-files');
  const libraryGrid = document.getElementById('library-grid');
  const sizeButtons = document.querySelectorAll('.size-btn');

  if (!searchInput || !libraryItems.length) return;

  let searchTimeout;

  // === GESTION DES TAILLES ===
  function initSizeControls() {
    sizeButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        // Retirer la classe active de tous les boutons
        sizeButtons.forEach(b => b.classList.remove('active'));
        // Ajouter la classe active au bouton cliqué
        btn.classList.add('active');
        
        // Changer la classe du grid
        const size = btn.dataset.size;
        libraryGrid.className = libraryGrid.className.replace(/size-\w+/, '');
        libraryGrid.classList.add(`size-${size}`);
        
        // Sauvegarder la préférence
        localStorage.setItem('libraryGridSize', size);
      });
    });
    
    // Restaurer la taille sauvegardée
    const savedSize = localStorage.getItem('libraryGridSize') || 'small';
    const savedBtn = document.querySelector(`[data-size="${savedSize}"]`);
    if (savedBtn) {
      sizeButtons.forEach(b => b.classList.remove('active'));
      savedBtn.classList.add('active');
      libraryGrid.className = libraryGrid.className.replace(/size-\w+/, '');
      libraryGrid.classList.add(`size-${savedSize}`);
    }
  }

  // === GESTION DE LA RECHERCHE ===
  function performSearch(query) {
    const normalizedQuery = query.toLowerCase().trim();
    let visibleCount = 0;
    let hideDelay = 0;
    let showDelay = 0;

    libraryItems.forEach((item, index) => {
      const filename = item.dataset.filename || '';
      const isMatch = normalizedQuery === '' || filename.includes(normalizedQuery);
      
      if (isMatch) {
        if (item.classList.contains('hidden') || item.classList.contains('hiding')) {
          setTimeout(() => {
            item.classList.remove('hidden', 'hiding');
            item.classList.add('showing');
            setTimeout(() => item.classList.remove('showing'), 300);
          }, showDelay * 30);
          showDelay++;
        }
        visibleCount++;
      } else {
        if (!item.classList.contains('hiding') && !item.classList.contains('hidden')) {
          setTimeout(() => {
            item.classList.add('hiding');
            setTimeout(() => {
              item.classList.remove('hiding');
              item.classList.add('hidden');
            }, 300);
          }, hideDelay * 20);
          hideDelay++;
        }
      }
    });

    if (clearSearch) {
      if (normalizedQuery !== '') {
        clearSearch.classList.add('show');
      } else {
        clearSearch.classList.remove('show');
      }
    }

    updateSearchResults(visibleCount, libraryItems.length, normalizedQuery);

    setTimeout(() => {
      if (noFilesMsg) {
        const actualVisible = document.querySelectorAll('.library-item:not(.hidden):not(.hiding)').length;
        if (actualVisible === 0 && normalizedQuery !== '') {
          noFilesMsg.textContent = `Aucun fichier trouvé pour "${query}"`;
          noFilesMsg.style.display = 'block';
        } else if (actualVisible === 0 && normalizedQuery === '') {
          noFilesMsg.textContent = 'Aucun fichier pour le moment.';
          noFilesMsg.style.display = 'block';
        } else {
          noFilesMsg.style.display = 'none';
        }
      }
    }, Math.max(hideDelay * 20, showDelay * 30) + 350);
  }

  function updateSearchResults(visible, total, query) {
    if (!searchResults) return;

    if (query === '') {
      searchResults.classList.remove('show');
      return;
    }

    if (visible === 0) {
      searchResults.textContent = 'Aucun résultat';
    } else if (visible === total) {
      searchResults.textContent = `${total} fichier${total > 1 ? 's' : ''}`;
    } else {
      searchResults.textContent = `${visible} sur ${total}`;
    }

    searchResults.classList.add('show');
  }

  function clearSearchInput() {
    searchInput.value = '';
    performSearch('');
    searchInput.focus();
  }

  // === ÉVÉNEMENTS ===
  searchInput.addEventListener('input', (e) => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => {
      performSearch(e.target.value);
    }, 150);
  });

  searchInput.addEventListener('keyup', (e) => {
    if (searchInput.value.trim() === '') {
      clearTimeout(searchTimeout);
      performSearch('');
    }
  });

  if (clearSearch) {
    clearSearch.addEventListener('click', clearSearchInput);
  }

  searchInput.addEventListener('blur', () => {
    setTimeout(() => {
      if (searchResults) searchResults.classList.remove('show');
    }, 200);
  });

  searchInput.addEventListener('focus', () => {
    if (searchInput.value.trim() !== '' && searchResults) {
      searchResults.classList.add('show');
    }
  });

  searchInput.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      clearSearchInput();
    }
  });

  // === INITIALISATION ===
  initSizeControls();
})();