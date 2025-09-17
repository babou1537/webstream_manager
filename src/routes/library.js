import express from 'express';
import multer from 'multer';
import path from 'path';
import fs from 'fs';
import { listLibraryFiles } from '../store.js';
import { deleteLibraryFile } from '../store.js'; // ajout
import { fileURLToPath } from 'url';

const router = express.Router();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const libraryDir = path.join(__dirname, '..', '..', 'storage', 'library');
fs.mkdirSync(libraryDir, { recursive: true });

const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, libraryDir);
  },
  filename: function (req, file, cb) {
    const original = file.originalname.replace(/[^a-zA-Z0-9._-]/g, '_');
    cb(null, original);
  }
});

const upload = multer({ storage });

// GET bibliothèque
router.get('/', (req, res) => {
  const files = listLibraryFiles();
  res.render('layout', {
    title: 'Bibliothèque',
    page: 'library',
    view: 'partials/library',
    files
  });
});

router.post('/upload', upload.array('files', 50), (req, res) => {
  res.redirect('/library');
});

// Supprimer un fichier de la bibliothèque
router.post('/delete', express.json(), (req, res) => {
  const { file } = req.body || {};
  if (!file) return res.status(400).json({ error: 'FILE_REQUIRED' });
  try {
    deleteLibraryFile(file);
    return res.json({ ok: true });
  } catch (e) {
    if (e.message === 'FILE_IN_USE') return res.status(409).json({ error: 'FILE_IN_USE' });
    if (e.message === 'FILE_NOT_FOUND') return res.status(404).json({ error: 'FILE_NOT_FOUND' });
    if (e.message === 'BAD_PATH') return res.status(400).json({ error: 'BAD_PATH' });
    console.error('Delete error:', e);
    return res.status(500).json({ error: 'SERVER_ERROR' });
  }
});

export default router;
