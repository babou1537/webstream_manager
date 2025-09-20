(() => {
  'use strict';

  // Helpers sûrs
  function cssEscape(s) {
    if (window.CSS && CSS.escape) return CSS.escape(s);
    return String(s).replace(/[^a-zA-Z0-9_-]/g, ch => '\\' + ch);
  }
  function num(v, dflt) {
    if (v == null || v === '') return dflt;
    const n = Number(String(v).replace(',', '.').trim());
    return Number.isFinite(n) ? n : dflt;
  }

  // Overlay singleton
  var overlay = document.querySelector('.preview-overlay');
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
          <div class="preview-layout single">
            <div class="preview-media">
              <div class="preview-frame natural" style="--w: 16; --h: 9;">
                <img class="preview-img" id="preview-img" alt="Aperçu" />
              </div>
            </div>
            <div class="preview-form" id="preview-form" hidden></div>
          </div>
        </div>
      </div>
    `;
    document.body.appendChild(overlay);
  }

  var dialog = overlay.querySelector('.preview-dialog');
  var layout = overlay.querySelector('.preview-layout');
  var formBox = overlay.querySelector('#preview-form');
  var imgEl = overlay.querySelector('#preview-img');
  var titleEl = overlay.querySelector('#preview-title');
  var deleteBtn = overlay.querySelector('.preview-delete');
  var previewFrame = overlay.querySelector('.preview-frame');

  var currentMode = 'library'; // 'library' | 'screen'
  var currentFile = null;
  var currentRef = null;

  function getScreensData() {
    var el = document.getElementById('screens-data');
    if (!el) return { families: [], library: [] };
    var fam = [], lib = [];
    try { fam = JSON.parse(el.dataset.families || '[]'); } catch (e) {}
    try { lib = JSON.parse(el.dataset.library || '[]'); } catch (e) {}
    return { families: fam, library: lib };
  }

  function openCommon(title, src) {
    titleEl.textContent = title || '';
    imgEl.src = src;
    overlay.classList.add('open');
    document.body.classList.add('body-modal-open');
  }

  function close() {
    overlay.classList.remove('open');
    document.body.classList.remove('body-modal-open');
  }

  // LIBRARY
  function openLibrary(img) {
    overlay.classList.add('mode-library');
    overlay.classList.remove('mode-create','mode-screen');
    currentMode = 'library';
    dialog.classList.remove('wide');
    layout.classList.add('single');
    formBox.hidden = true;
    deleteBtn.hidden = false;

    previewFrame.classList.add('natural'); // ratio naturel

    var src = img.src;
    var filename = '';
    try {
      var ix = src.lastIndexOf('/content/');
      filename = ix >= 0 ? decodeURIComponent(src.substring(ix + 9)) : '';
    } catch (e) {}
    currentFile = filename;
    currentRef = null;

    openCommon(filename, src);
  }

  // Fonction pour générer l'URL du placeholder dynamique
  function getPlaceholderUrl(width, height) {
    const w = Math.round((width || 16) * 100);
    const h = Math.round((height || 9) * 100);
    return `/screens/placeholder/${w}/${h}.svg`;
  }

  // Fonction pour contraindre la taille de la preview - SIMPLIFIÉE
  function constrainPreviewSize(w, h) {
    // Supprimer les styles inline qui interfèrent
    previewFrame.style.removeProperty('max-width');
    previewFrame.style.removeProperty('max-height');
    previewFrame.style.removeProperty('width');
    previewFrame.style.removeProperty('height');
    
    // Laisser CSS faire le travail avec aspect-ratio et les nouvelles règles
    console.log(`Preview contrainte : ${w}×${h}`);
  }

  // Fonction pour mettre à jour le compteur de famille
  function updateFamilyCounter(cardToRemove) {
    if (!cardToRemove) return;
    
    // Trouver la famille parente
    var familyGroup = cardToRemove.closest('.family-group');
    if (!familyGroup) return;
    
    var summary = familyGroup.querySelector('summary');
    var screenGrid = familyGroup.querySelector('.screen-grid');
    
    if (!summary || !screenGrid) return;
    
    // Compter les cartes restantes (sans celle qui va être supprimée)
    var remainingCards = screenGrid.querySelectorAll('.screen-card:not([style*="fadeOut"])').length - 1;
    
    // Mettre à jour le texte du summary
    var familyName = summary.querySelector('strong');
    if (familyName) {
      var familyNameText = familyName.textContent;
      summary.innerHTML = '<strong>' + familyNameText + '</strong> — ' + remainingCards + ' écran' + (remainingCards > 1 ? 's' : '');
    }
    
    // Si plus d'écrans, masquer le groupe famille
    if (remainingCards === 0) {
      setTimeout(function() {
        familyGroup.style.animation = 'fadeOut 0.3s ease forwards';
        setTimeout(function() {
          if (familyGroup.parentNode) familyGroup.parentNode.removeChild(familyGroup);
          
          // Vérifier s'il reste des familles
          var allFamilyGroups = document.querySelectorAll('.family-group');
          if (allFamilyGroups.length === 0) {
            // Afficher message "Aucune famille"
            var contentArea = document.querySelector('.content-area');
            if (contentArea) {
              contentArea.innerHTML = '<p class="muted">Aucune famille</p>';
            }
          }
        }, 300);
      }, 400); // Délai pour laisser la carte disparaître d'abord
    }
  }

  // Fonction pour déplacer une carte vers la bonne famille en mode "family"
  function moveCardToFamily(tile, famId, famName) {
    if (!tile) return;
    
    var card = tile.closest('.screen-card');
    if (!card) return;
    
    // Trouver la section famille cible
    var targetSection = null;
    if (famId) {
      // Chercher la famille par nom dans les summary
      var summaries = document.querySelectorAll('.family-group summary');
      for (var i = 0; i < summaries.length; i++) {
        if (summaries[i].textContent.indexOf(famName) > -1) {
          targetSection = summaries[i].parentNode.querySelector('.screen-grid');
          break;
        }
      }
    } else {
      // Chercher "Sans famille"
      var summaries = document.querySelectorAll('.family-group summary');
      for (var i = 0; i < summaries.length; i++) {
        if (summaries[i].textContent.indexOf('Sans famille') > -1) {
          targetSection = summaries[i].parentNode.querySelector('.screen-grid');
          break;
        }
      }
    }
    
    if (targetSection) {
      // Animation de déplacement
      card.style.animation = 'slideOut 0.3s ease forwards';
      setTimeout(function() {
        if (targetSection && card.parentNode) {
          card.parentNode.removeChild(card);
          targetSection.appendChild(card);
          card.style.animation = 'slideIn 0.3s ease forwards';
        }
      }, 300);
    }
  }

  // Feedback visuel de sauvegarde réussie
  function showSaveSuccess(button) {
    if (!button) return;
    
    var originalText = button.textContent;
    var originalColor = button.style.backgroundColor;
    
    button.textContent = '✓ Sauvé';
    button.style.backgroundColor = '#10b981';
    button.style.color = '#fff';
    button.disabled = true;
    
    setTimeout(function() {
      button.textContent = originalText;
      button.style.backgroundColor = originalColor;
      button.style.color = '';
      button.disabled = false;
    }, 1500);
  }

  // CRÉATION D'ÉCRAN
  function openCreateScreen() {
    overlay.classList.add('mode-create');
    overlay.classList.remove('mode-library','mode-screen');
    currentMode = 'create';
    dialog.classList.add('wide');
    layout.classList.remove('single');
    formBox.hidden = false;
    deleteBtn.hidden = true;

    // Placeholder avec ratio par défaut - SIMPLE
    previewFrame.classList.remove('natural');
    previewFrame.style.setProperty('--w', '16');
    previewFrame.style.setProperty('--h', '9');
    
    constrainPreviewSize(16, 9);

    var data = getScreensData();
    
    openCommon('Nouvel écran', getPlaceholderUrl(16, 9));

    // Formulaire de création
    var famOptions = ['<option value="">— Aucune —</option>']
      .concat((data.families || []).map(function (f) {
        return '<option value="' + String(f.id) + '">' + f.name + '</option>';
      }))
      .join('');

    formBox.innerHTML = ''
      + '<h3>Créer un écran</h3>'
      + '<form class="create-form" method="post" action="/screens/create">'
      + '  <div class="form-row">'
      + '    <label for="create-ref">Référence</label>'
      + '    <input id="create-ref" name="ref" placeholder="screen_001" required />'
      + '  </div>'
      + '  <div class="form-row">'
      + '    <label for="create-family">Famille</label>'
      + '    <select id="create-family" name="familyId">' + famOptions + '</select>'
      + '  </div>'
      + '  <div class="form-row">'
      + '    <label>Dimensions</label>'
      + '    <div class="dimensions-row">'
      + '      <input id="create-width" name="width" type="number" step="0.1" min="0" placeholder="16" value="16" />'
      + '      <div class="separator">×</div>'
      + '      <input id="create-height" name="height" type="number" step="0.1" min="0" placeholder="9" value="9" />'
      + '    </div>'
      + '  </div>'
      + '  <div class="form-actions">'
      + '    <button type="button" class="btn secondary" data-action="close">Annuler</button>'
      + '    <button type="submit" class="btn primary">Créer l\'écran</button>'
      + '  </div>'
      + '</form>';

    // Preview en temps réel des dimensions SIMPLIFIÉ
    var widthInput = formBox.querySelector('#create-width');
    var heightInput = formBox.querySelector('#create-height');
    
    function updatePreview() {
      var w = Math.max(0.1, num(widthInput.value, 16));
      var h = Math.max(0.1, num(heightInput.value, 9));
      
      previewFrame.style.setProperty('--w', String(w));
      previewFrame.style.setProperty('--h', String(h));
      
      constrainPreviewSize(w, h);
      
      imgEl.src = getPlaceholderUrl(w, h);
    }
    
    if (widthInput) widthInput.addEventListener('input', updatePreview);
    if (heightInput) heightInput.addEventListener('input', updatePreview);
  }

  // SCREENS
  function openScreen(tile) {
    var ds = tile.dataset || {};
    var imgTag = tile.querySelector('img');

    overlay.classList.add('mode-screen');
    overlay.classList.remove('mode-library','mode-create');
    currentMode = 'screen';
    dialog.classList.add('wide');
    layout.classList.remove('single');
    formBox.hidden = false;
    deleteBtn.hidden = true;

    // ratio forcé - SIMPLIFIÉ
    var w = Math.max(0.1, num(ds.width, 16));
    var h = Math.max(0.1, num(ds.height, 9));
    previewFrame.classList.remove('natural');
    previewFrame.style.setProperty('--w', String(w));
    previewFrame.style.setProperty('--h', String(h));
    
    constrainPreviewSize(w, h);

    var data = getScreensData();

    currentRef = ds.ref || null;
    currentFile = ds.content || null;

    const imgSrc = (imgTag && imgTag.src) ? imgTag.src : getPlaceholderUrl(w, h);
    openCommon(currentRef || 'Écran', imgSrc);

    // Formulaire à droite - AVEC MODE ÉDITION
    var famOptions = ['<option value="">— Aucune —</option>']
      .concat((data.families || []).map(function (f) {
        var selected = String(ds.familyId || '') === String(f.id) ? ' selected' : '';
        return '<option value="' + String(f.id) + '"' + selected + '>' + f.name + '</option>';
      }))
      .join('');
    var libOptionsFirst = '<option value="" disabled ' + (currentFile ? '' : 'selected') + '>Choisir…</option>';
    var libOptions = libOptionsFirst + (data.library || []).map(function (f) {
      var sel = currentFile === f ? ' selected' : '';
      return '<option value="' + f + '"' + sel + '>' + f + '</option>';
    }).join('');

    formBox.innerHTML = ''
      + '<div class="screen-header">'
      + '  <h3>Modifier l\'écran</h3>'
      + '  <button type="button" class="edit-toggle" id="edit-toggle" title="Activer l\'édition">'
      + '    <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">'
      + '      <path d="M12.146.146a.5.5 0 0 1 .708 0l3 3a.5.5 0 0 1 0 .708L8.5 11.207l-3 1a.5.5 0 0 1-.638-.638l1-3L13.207.854a.5.5 0 0 1 .939.146zM14.5 3L13 1.5 4.5 10 4 12l2-0.5L14.5 3z"/>'
      + '    </svg>'
      + '  </button>'
      + '</div>'
      + '<div class="edit-content" id="edit-content">'
      + '  <form class="row edit-form" method="post" action="/screens/' + encodeURIComponent(currentRef) + '/family">'
      + '    <label>Famille</label>'
      + '    <select name="familyId" disabled>' + famOptions + '</select>'
      + '    <button class="btn primary edit-btn" type="submit" style="display: none;">OK</button>'
      + '  </form>'
      + '  <form class="row edit-form" method="post" action="/screens/' + encodeURIComponent(currentRef) + '/dimensions">'
      + '    <label>Dimensions</label>'
      + '    <input name="width" id="preview-form-input-width" type="number" step="0.1" min="0" value="' + (ds.width || '') + '" placeholder="L" disabled />'
      + '    × '
      + '    <input name="height" id="preview-form-input-height" type="number" step="0.1" min="0" value="' + (ds.height || '') + '" placeholder="H" disabled />'
      + '    <button class="btn primary edit-btn" type="submit" style="display: none;">💾</button>'
      + '  </form>'
      + '  <form class="row edit-form" method="post" action="/screens/' + encodeURIComponent(currentRef) + '/assign">'
      + '    <label>Contenu</label>'
      + '    <select name="file" required disabled>' + libOptions + '</select>'
      + '    <button class="btn primary edit-btn" type="submit" style="display: none;">Assigner</button>'
      + '  </form>'
      + '  <form class="row edit-form" method="post" action="/screens/' + encodeURIComponent(currentRef) + '/unassign">'
      + '    <button class="btn edit-btn" type="submit" ' + (currentFile ? '' : 'disabled') + ' style="display: none;">Déassigner</button>'
      + '  </form>'
      + '</div>'
      + '<form class="row" method="post" action="/screens/' + encodeURIComponent(currentRef) + '/delete" onsubmit="return confirm(\'Supprimer ' + currentRef + ' ?\');">'
      + '  <button class="btn danger" type="submit">Supprimer l\'écran</button>'
      + '</form>';

    // Gestionnaire du bouton d'édition
    var editToggle = formBox.querySelector('#edit-toggle');
    var editContent = formBox.querySelector('#edit-content');
    var isEditMode = false;

    if (editToggle) {
      editToggle.addEventListener('click', function() {
        isEditMode = !isEditMode;
        toggleEditMode(isEditMode);
      });
    }

    function toggleEditMode(enable) {
      var inputs = editContent.querySelectorAll('input, select');
      var buttons = editContent.querySelectorAll('.edit-btn');
      
      if (enable) {
        // Mode édition activé
        editToggle.classList.add('active');
        editToggle.title = 'Désactiver l\'édition';
        
        inputs.forEach(function(input) {
          input.disabled = false;
        });
        
        buttons.forEach(function(btn) {
          btn.style.display = '';
        });
        
      } else {
        // Mode édition désactivé  
        editToggle.classList.remove('active');
        editToggle.title = 'Activer l\'édition';
        
        inputs.forEach(function(input) {
          input.disabled = true;
        });
        
        buttons.forEach(function(btn) {
          btn.style.display = 'none';
        });
      }
    }
  }

  // Délégation clic: LIBRARY
  document.addEventListener('click', function (e) {
    var libImg = e.target && e.target.closest ? e.target.closest('#library-grid img.previewable') : null;
    if (libImg) {
      e.preventDefault();
      openLibrary(libImg);
    }
  });

  // Délégation clic: SCREENS
  document.addEventListener('click', function (e) {
    var tile = e.target && e.target.closest ? e.target.closest('.screen-tile') : null;
    if (tile) {
      e.preventDefault();
      openScreen(tile);
    }
  });

  // Clic sur "Ajouter un écran"
  document.addEventListener('click', function (e) {
    if (e.target && e.target.id === 'addScreenBtn') {
      e.preventDefault();
      console.log('Clic sur Ajouter un écran détecté'); // DEBUG
      openCreateScreen();
    }
  });

  // Actions overlay
  overlay.addEventListener('click', function (e) {
    var target = e.target || e.srcElement;
    var act = target && target.dataset ? target.dataset.action : undefined;

    if (act === 'close') {
      e.preventDefault();
      close();
      return;
    }

    if (act === 'delete' && currentMode === 'library') {
      if (!currentFile) return;
      var ok = window.confirm('Supprimer le fichier "' + currentFile + '" de la bibliothèque ?');
      if (!ok) return;

      fetch('/library/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify({ file: currentFile })
      })
      .then(function (res) {
        if (!res.ok) return res.json().catch(function(){ return {}; }).then(function (d){ throw d; });
        return res.json().catch(function () { return {}; });
      })
      .then(function () {
        var sel = '#library-grid img[data-filename="' + cssEscape(currentFile) + '"]';
        var img = document.querySelector(sel);
        if (img && img.closest) {
          var card = img.closest('.card');
          if (card && card.parentNode) card.parentNode.removeChild(card);
        }
        close();
      })
      .catch(function (err) {
        var code = err && err.error;
        if (code === 'FILE_IN_USE') alert('Ce fichier est assigné à un écran. Déassigner avant de supprimer.');
        else if (code === 'FILE_NOT_FOUND') alert('Fichier introuvable.');
        else alert('Erreur lors de la suppression.');
      });
    }
  });

  window.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && overlay.classList.contains('open')) close();
  });

  // POST AJAX (URLSearchParams au lieu de FormData)
  function postForm(form) {
    console.log('=== postForm DEBUG ===');
    console.log('Form:', form);
    
    // Créer URLSearchParams au lieu de FormData
    const params = new URLSearchParams();
    const inputs = form.querySelectorAll('input, select, textarea');
    inputs.forEach(input => {
      if (input.name && input.value !== '') {
        params.append(input.name, input.value);
        console.log(`Param: ${input.name} = "${input.value}"`);
      }
    });
    
    console.log('URLSearchParams:', params.toString());
    
    return fetch(form.action, {
      method: 'POST',
      body: params,
      headers: { 
        'Accept': 'application/json',
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      redirect: 'follow'
    }).then(function (res) {
      console.log('Response status:', res.status);
      if (!res.ok) return null;
      var ct = res.headers.get('content-type') || '';
      if (ct.indexOf('application/json') >= 0) return res.json().catch(function(){ return {}; });
      return {};
    });
  }

  // Intercepter les submits des formulaires dans l'overlay
  formBox.addEventListener('submit', function (e) {
    console.log('=== SUBMIT INTERCEPTÉ ===');
    console.log('Event target:', e.target);
    
    var form = e.target && e.target.closest ? e.target.closest('form') : null;
    console.log('Form trouvé:', form);
    
    if (!form) {
      console.log('ERREUR: Aucun formulaire trouvé');
      return;
    }
    e.preventDefault();

    // Création d'écran - redirection classique
    if (form.action.indexOf('/screens/create') > -1) {
      console.log('Création d\'écran - soumission classique');
      form.submit(); // Soumission normale pour redirection
      return;
    }

    console.log('Appel postForm...');
    postForm(form).then(function (data) {
      console.log('Retour postForm:', data);
      if (!data) { 
        console.log('ERREUR: Pas de données retournées');
        alert("Erreur lors de l'opération."); 
        return; 
      }

      var tileSel = '.screen-tile[data-ref="' + cssEscape(currentRef) + '"]';
      var tile = currentRef ? document.querySelector(tileSel) : null;
      var tileImg = tile ? tile.querySelector('img') : null;

      if (form.action.indexOf('/dimensions') > -1) {
        var w = (data.width != null) ? data.width : num(form.width.value, 16);
        var h = (data.height != null) ? data.height : num(form.height.value, 9);
        
        // MAJ overlay - FORCER LA MISE À JOUR
        previewFrame.classList.remove('natural');
        previewFrame.style.setProperty('--w', String(w));
        previewFrame.style.setProperty('--h', String(h));
        
        // Forcer le reflow pour appliquer le nouveau ratio
        previewFrame.offsetHeight; // Trigger reflow
        
        // MAJ tuile + DOM
        if (tile) {
          tile.dataset.width = String(w);
          tile.dataset.height = String(h);
          var scSub = tile.querySelector('.sc-sub');
          if (scSub) scSub.textContent = w + ' × ' + h;
          var frame = tile.querySelector('.sc-frame');
          if (frame) {
            frame.style.setProperty('--w', String(w));
            frame.style.setProperty('--h', String(h));
          }
        }
        
        // Feedback visuel
        showSaveSuccess(form.querySelector('button[type="submit"]'));
        
      } else if (form.action.indexOf('/family') > -1) {
        var famId = form.familyId ? form.familyId.value : '';
        var famName = '';
        
        // Trouver le nom de la famille
        var famSelect = form.querySelector('select[name="familyId"]');
        if (famSelect) {
          var selectedOption = famSelect.querySelector('option[value="' + famId + '"]');
          famName = selectedOption ? selectedOption.textContent : 'Sans famille';
        }
        
        // MAJ dataset de la tuile
        if (tile) {
          tile.dataset.familyId = famId;
          // En mode "all", MAJ l'affichage famille
          var famDiv = tile.querySelector('.sc-family');
          if (famDiv) famDiv.textContent = famName;
        }
        
        // MAJ du <select> dans l'overlay
        if (famSelect) famSelect.value = famId || '';
        
        // Si on est en mode "family", déplacer la carte vers la bonne section
        if (window.location.search.indexOf('mode=family') > -1 || window.location.search.indexOf('mode=') === -1) {
          moveCardToFamily(tile, famId, famName);
        }
        
        // Feedback visuel
        showSaveSuccess(form.querySelector('button[type="submit"]'));
        
      } else if (form.action.indexOf('/assign') > -1) {
        var file = form.file ? form.file.value : '';
        currentFile = file;
        var src = '/content/' + encodeURIComponent(file);
        
        // MAJ overlay
        imgEl.src = src;
        
        // MAJ tuile
        if (tileImg) tileImg.src = src;
        
        // MAJ bouton déassigner
        var unSel = 'form[action$="/' + encodeURIComponent(currentRef) + '/unassign"] button';
        var unBtn = formBox.querySelector(unSel);
        if (unBtn) unBtn.disabled = false;
        
        // Feedback visuel
        showSaveSuccess(form.querySelector('button[type="submit"]'));
        
      } else if (form.action.indexOf('/unassign') > -1) {
        currentFile = '';
        
        // MAJ overlay avec placeholder dynamique
        const w = num(previewFrame.style.getPropertyValue('--w'), 16);
        const h = num(previewFrame.style.getPropertyValue('--h'), 9);
        imgEl.src = getPlaceholderUrl(w, h);
        
        // MAJ tuile avec placeholder dynamique
        if (tileImg) tileImg.src = getPlaceholderUrl(w, h);
        
        // MAJ bouton déassigner
        var unBtn2 = form.querySelector('button');
        if (!unBtn2) {
          var unSel2 = 'form[action$="/' + encodeURIComponent(currentRef) + '/unassign"] button';
          unBtn2 = formBox.querySelector(unSel2);
        }
        if (unBtn2) unBtn2.disabled = true;
        
        // Feedback visuel
        showSaveSuccess(unBtn2);
        
      } else if (form.action.indexOf('/delete') > -1) {
        // Supprimer la carte
        if (tile && tile.closest) {
          var card = tile.closest('.screen-card');
          if (card) {
            // Mettre à jour le compteur de famille AVANT suppression
            updateFamilyCounter(card);
            
            card.style.animation = 'fadeOut 0.3s ease forwards';
            setTimeout(function() {
              if (card.parentNode) card.parentNode.removeChild(card);
            }, 300);
          }
        }
        
        // Fermer overlay après animation
        setTimeout(close, 100);
      }
    });
  });

})();