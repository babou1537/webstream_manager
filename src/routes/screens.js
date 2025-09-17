import express from 'express';
import {
  listFamilies, listLibraryFiles,
  listScreens, listScreensByFamily,
  createScreen, setScreenFamily, assignContent, unassignContent,
  deleteScreen, setDimensions
} from '../store.js';

const router = express.Router();

// GET /screens?mode=all|family (défaut: family)
router.get('/', (req, res) => {
  const mode = (req.query.mode === 'all') ? 'all' : 'family'; // family par défaut
  const families = listFamilies();
  const libraryFiles = listLibraryFiles();
  const screens = (mode === 'family') ? listScreensByFamily() : listScreens();

  res.render('layout', {
    title: 'Écrans',
    page: 'screens',
    view: 'partials/screens',
    mode,
    families,
    libraryFiles,
    screens
  });
});

function parseNum(v) {
  if (v == null || v === '') return null;
  const n = Number(String(v).replace(',', '.').trim());
  return Number.isFinite(n) ? n : null;
}

// Créer un écran
router.post('/create', (req, res) => {
  const { ref, familyId, width, height } = req.body;
  if (!ref) return res.status(400).send('ref required');
  try {
    createScreen({
      ref: String(ref).trim(),
      familyId: familyId ? Number(familyId) : null,
      width: parseNum(width),
      height: parseNum(height),
    });
    res.redirect('/screens');
  } catch (e) {
    if (e.message === 'SCREEN_EXISTS') return res.status(409).send('Cet écran existe déjà');
    throw e;
  }
});

// Changer la famille
router.post('/:ref/family', (req, res) => {
  console.log('=== POST /family DEBUG ===');
  console.log('Params:', req.params);
  console.log('Body brut:', req.body);
  console.log('Headers Accept:', req.headers.accept);
  
  const famId = req.body.familyId ? Number(req.body.familyId) : null;
  console.log('familyId parsé:', typeof famId, famId);
  
  try {
    console.log('Appel setScreenFamily avec:', req.params.ref, famId);
    setScreenFamily(req.params.ref, famId);
    console.log('setScreenFamily OK');
    
    // overlay attend JSON si Accept: application/json
    if ((req.headers.accept || '').includes('application/json')) {
      console.log('Réponse JSON');
      return res.json({ ok: true, familyId: famId });
    }
    console.log('Redirection classique');
    res.redirect('/screens');
  } catch (e) {
    console.error('ERREUR setScreenFamily:', e);
    if (e.message === 'SCREEN_NOT_FOUND') {
      if ((req.headers.accept || '').includes('application/json')) return res.status(404).json({ error: 'SCREEN_NOT_FOUND' });
      return res.status(404).send('Écran introuvable');
    }
    throw e;
  }
});

// Assigner contenu
router.post('/:ref/assign', (req, res) => {
  try {
    assignContent(req.params.ref, req.body.file);
    if ((req.headers.accept || '').includes('application/json')) return res.json({ ok: true, file: req.body.file });
    res.redirect('/screens');
  } catch (e) {
    const json = (req.headers.accept || '').includes('application/json');
    if (e.message === 'SCREEN_NOT_FOUND') return json ? res.status(404).json({ error: 'SCREEN_NOT_FOUND' }) : res.status(404).send('Écran introuvable');
    if (e.message === 'CONTENT_NOT_FOUND') return json ? res.status(404).json({ error: 'CONTENT_NOT_FOUND' }) : res.status(404).send('Contenu introuvable');
    throw e;
  }
});

// Déassigner
router.post('/:ref/unassign', (req, res) => {
  try {
    unassignContent(req.params.ref);
    if ((req.headers.accept || '').includes('application/json')) return res.json({ ok: true });
    res.redirect('/screens');
  } catch (e) {
    if (e.message === 'SCREEN_NOT_FOUND') {
      if ((req.headers.accept || '').includes('application/json')) return res.status(404).json({ error: 'SCREEN_NOT_FOUND' });
      return res.status(404).send('Écran introuvable');
    }
    throw e;
  }
});

// Supprimer un écran
router.post('/:ref/delete', (req, res) => {
  try {
    deleteScreen(req.params.ref);
    if ((req.headers.accept || '').includes('application/json')) return res.json({ ok: true });
    res.redirect('/screens');
  } catch (e) {
    if (e.message === 'SCREEN_NOT_FOUND') {
      if ((req.headers.accept || '').includes('application/json')) return res.status(404).json({ error: 'SCREEN_NOT_FOUND' });
      return res.status(404).send('Écran introuvable');
    }
    throw e;
  }
});

// Enregistrer dimensions
router.post('/:ref/dimensions', (req, res) => {
  console.log('=== POST /dimensions DEBUG ===');
  console.log('Params:', req.params);
  console.log('Body brut:', req.body);
  console.log('Headers Accept:', req.headers.accept);
  
  const { width, height } = req.body;
  console.log('width brut:', typeof width, width);
  console.log('height brut:', typeof height, height);
  
  const w = parseNum(req.body.width);
  const h = parseNum(req.body.height);
  console.log('width parsé:', typeof w, w);
  console.log('height parsé:', typeof h, h);
  
  try {
    console.log('Appel setDimensions avec:', req.params.ref, w, h);
    setDimensions(req.params.ref, w, h);
    console.log('setDimensions OK');
    
    if ((req.headers.accept || '').includes('application/json')) {
      console.log('Réponse JSON');
      return res.json({ ok: true, width: w, height: h });
    }
    console.log('Redirection classique');
    res.redirect('/screens');
  } catch (e) {
    console.error('ERREUR setDimensions:', e);
    if (e.message === 'SCREEN_NOT_FOUND') {
      if ((req.headers.accept || '').includes('application/json')) return res.status(404).json({ error: 'SCREEN_NOT_FOUND' });
      return res.status(404).send('Écran introuvable');
    }
    throw e;
  }
});

// Générer placeholder SVG dynamique
router.get('/placeholder/:width/:height.svg', (req, res) => {
  const width = Math.max(100, Math.min(2000, parseInt(req.params.width) || 640));
  const height = Math.max(100, Math.min(2000, parseInt(req.params.height) || 400));
  
  // Calculer les proportions pour les éléments internes
  const margin = Math.min(width, height) * 0.05; // 5% de marge
  const rectWidth = width - (margin * 2);
  const rectHeight = height - (margin * 2);
  const fontSize = Math.min(width, height) * 0.04; // Police adaptative
  
  // Points du graphique adaptatifs
  const graphPoints = [
    `M${margin + rectWidth * 0.1} ${height - margin * 2}`,
    `l${rectWidth * 0.15} ${-rectHeight * 0.3}`,
    `l${rectWidth * 0.15} ${rectHeight * 0.2}`,
    `l${rectWidth * 0.13} ${-rectHeight * 0.25}`,
    `l${rectWidth * 0.2} ${rectHeight * 0.35}`,
    `H${margin + rectWidth * 0.1}z`
  ].join(' ');

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none">
  <defs>
    <linearGradient id="g" x1="0" x2="1" y1="0" y2="1">
      <stop offset="0" stop-color="#1f2937"/>
      <stop offset="1" stop-color="#0f172a"/>
    </linearGradient>
  </defs>
  <rect width="${width}" height="${height}" fill="url(#g)"/>
  <g fill="none" stroke="#334155" stroke-width="2">
    <rect x="${margin}" y="${margin}" width="${rectWidth}" height="${rectHeight}" rx="10"/>
    <path d="${graphPoints}" stroke="#475569" fill="rgba(71, 85, 105, 0.2)"/>
  </g>
  <text x="50%" y="50%" fill="#94a3b8" font-family="Segoe UI,Roboto,sans-serif" font-size="${fontSize}" text-anchor="middle" dominant-baseline="middle">
    Aucun contenu
  </text>
</svg>`;

  res.setHeader('Content-Type', 'image/svg+xml');
  res.setHeader('Cache-Control', 'public, max-age=3600');
  res.send(svg);
});

export default router;
