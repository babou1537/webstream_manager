import express from 'express';
import { listFamilies, createFamily, deleteFamily } from '../store.js';

const router = express.Router();

router.get('/', (req, res) => {
  const families = listFamilies();
  res.render('layout', { title: 'Familles', page: 'families', view: 'partials/families', families });
});

router.post('/create', express.urlencoded({ extended: false }), (req, res) => {
  try {
    createFamily(req.body.name);
    res.redirect('/families');
  } catch (e) {
    if (e.message === 'FAMILY_EXISTS') return res.status(409).send('Famille déjà existante');
    if (e.message === 'FAMILY_NAME_REQUIRED') return res.status(400).send('Nom requis');
    throw e;
  }
});

router.post('/:id/delete', (req, res) => {
  try {
    deleteFamily(Number(req.params.id));
    res.redirect('/families');
  } catch (e) {
    if (e.message === 'FAMILY_IN_USE') return res.status(409).send('Famille utilisée par des écrans');
    if (e.message === 'FAMILY_NOT_FOUND') return res.status(404).send('Famille introuvable');
    throw e;
  }
});

export default router;
