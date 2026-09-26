import express from 'express';
import multer from 'multer';
import fs from 'fs';
import os from 'os';
import path from 'path';
import { exportData, importData, readSqliteExport } from '../store.js';
import { requireWrite } from '../permissions.js';

const router = express.Router();

// Noms de fichiers temporaires imposés : jamais le nom envoyé par le client
const TEMP_NAMES = [
  [/\.db-wal$/i, 'import.db-wal'],
  [/\.db-shm$/i, 'import.db-shm'],
  [/\.(db|sqlite3?)$/i, 'import.db'],
  [/\.json$/i, 'import.json']
];
const tempNameFor = (original) => TEMP_NAMES.find(([re]) => re.test(original))?.[1];

const upload = multer({
  storage: multer.diskStorage({
    destination(req, file, cb) {
      req.importDir ??= fs.mkdtempSync(path.join(os.tmpdir(), 'wsm-import-'));
      cb(null, req.importDir);
    },
    filename(req, file, cb) {
      cb(null, tempNameFor(file.originalname));
    }
  }),
  fileFilter(req, file, cb) {
    cb(null, Boolean(tempNameFor(file.originalname)));
  },
  limits: { files: 3, fileSize: 100 * 1024 * 1024 }
});

const ERRORS = {
  IMPORT_NO_FILE: 'Aucun fichier valide reçu (.json ou .db attendu).',
  IMPORT_INVALID: 'Fichier invalide : liste d\'écrans introuvable.',
  IMPORT_TOO_LARGE: 'Trop d\'écrans dans ce fichier.',
  IMPORT_BAD_MODE: 'Mode d\'import inconnu.',
  IMPORT_NOT_A_DATABASE: 'Ce fichier n\'est pas une base SQLite lisible.',
  IMPORT_NO_SCREENS_TABLE: 'Cette base ne contient pas de table « screens ».',
  IMPORT_BAD_JSON: 'Ce fichier JSON est illisible.'
};

const renderPage = (res, extra = {}) => res.render('layout', {
  title: 'Données',
  page: 'data',
  view: 'partials/data',
  result: null,
  error: null,
  ...extra
});

router.get('/', (req, res) => renderPage(res));

router.get('/export', async (req, res) => {
  const data = await exportData();
  const day = new Date().toISOString().slice(0, 10);
  res.set('Content-Disposition', `attachment; filename="webstream-export-${day}.json"`);
  res.json(data);
});

const cleanup = (req) => {
  if (req.importDir) fs.rmSync(req.importDir, { recursive: true, force: true });
};

const receive = (req, res, next) => {
  upload.array('files', 3)(req, res, (err) => {
    if (!err) return next();
    cleanup(req);
    res.status(400);
    return renderPage(res, { error: `Fichier refusé : ${err.message}` });
  });
};

router.post('/import', requireWrite, receive, async (req, res) => {
  const files = new Set((req.files || []).map(f => f.filename));
  try {
    let data;
    if (files.has('import.db')) {
      data = await readSqliteExport(path.join(req.importDir, 'import.db'));
    } else if (files.has('import.json')) {
      try {
        data = JSON.parse(fs.readFileSync(path.join(req.importDir, 'import.json'), 'utf8'));
      } catch {
        throw new Error('IMPORT_BAD_JSON');
      }
    } else {
      throw new Error('IMPORT_NO_FILE');
    }
    const result = await importData(data, req.body?.mode);
    return renderPage(res, { result });
  } catch (e) {
    if (ERRORS[e.message]) {
      res.status(400);
      return renderPage(res, { error: ERRORS[e.message] });
    }
    console.error('Import error:', e);
    res.status(500);
    return renderPage(res, { error: "Erreur serveur pendant l'import." });
  } finally {
    cleanup(req);
  }
});

export default router;
