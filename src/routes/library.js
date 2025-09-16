import express from 'express';
import multer from 'multer';
import path from 'path';
import fs from 'fs';
import { listLibraryFiles } from '../store.js';
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

export default router;
