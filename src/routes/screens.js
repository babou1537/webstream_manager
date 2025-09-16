import express from 'express';
import {
  listScreens, createScreen, assignContent,
  listLibraryFiles, unassignContent, deleteScreen, setDimensions
} from '../store.js';

const router = express.Router();

router.get('/', (req, res) => {
  const screens = listScreens();
  const libraryFiles = listLibraryFiles();
  res.render('layout', {
    title: 'Écrans',
    page: 'screens',
    view: 'partials/screens',
    screens,
    libraryFiles
  });
});

router.post('/create', (req, res) => {
  const { ref, family, width, height } = req.body;
  if (!ref) return res.status(400).send('ref required');
  try {
    createScreen({
      ref: String(ref).trim(),
      family: family ? String(family).trim() : null,
      width: width ? Number(width) : null,
      height: height ? Number(height) : null
    });
    res.redirect('/screens');
  } catch (e) {
    if (e.message === 'SCREEN_EXISTS') return res.status(409).send('Cet écran existe déjà');
    throw e;
  }
});

router.post('/:ref/assign', (req, res) => {
  const { ref } = req.params;
  const { file } = req.body;
  try {
    assignContent(ref, file);
    res.redirect('/screens');
  } catch (e) {
    if (e.message === 'SCREEN_NOT_FOUND') return res.status(404).send('Écran introuvable');
    if (e.message === 'CONTENT_NOT_FOUND') return res.status(404).send('Contenu introuvable');
    throw e;
  }
});

router.post('/:ref/unassign', (req, res) => {
  const { ref } = req.params;
  try {
    unassignContent(ref);
    res.redirect('/screens');
  } catch (e) {
    if (e.message === 'SCREEN_NOT_FOUND') return res.status(404).send('Écran introuvable');
    throw e;
  }
});

router.post('/:ref/delete', (req, res) => {
  const { ref } = req.params;
  try {
    deleteScreen(ref);
    res.redirect('/screens');
  } catch (e) {
    if (e.message === 'SCREEN_NOT_FOUND') return res.status(404).send('Écran introuvable');
    throw e;
  }
});

router.post('/:ref/dimensions', (req, res) => {
  const { ref } = req.params;
  const { width, height } = req.body;
  try {
    setDimensions(ref, width ? Number(width) : null, height ? Number(height) : null);
    res.redirect('/screens');
  } catch (e) {
    if (e.message === 'SCREEN_NOT_FOUND') return res.status(404).send('Écran introuvable');
    throw e;
  }
});

export default router;
