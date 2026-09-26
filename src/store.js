import path from 'path';
import fs from 'fs';
import sqlite3 from 'sqlite3';
import { open } from 'sqlite';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

let dbPath = path.join(__dirname, 'db', 'webstream.db');
const libraryDir = path.resolve(__dirname, '..', 'storage', 'library');

// Promise-based database handle
let db;
let dbInitialized = false;

// Fonction pour définir le chemin de la base de données
export function setDatabasePath(newPath) {
  if (dbInitialized) {
    console.warn('[Store] Database already initialized, cannot change path');
    return;
  }
  dbPath = newPath;
  console.log(`[Store] Database path set to: ${dbPath}`);

  // Créer le répertoire parent si nécessaire
  fs.mkdirSync(path.dirname(dbPath), { recursive: true });
}

// Créer le répertoire de la bibliothèque
fs.mkdirSync(libraryDir, { recursive: true });

async function getDb() {
  if (!db) {
    db = await open({ filename: dbPath, driver: sqlite3.Database });
    await initializeDatabase();
    dbInitialized = true;
  }
  return db;
}

// Debug helpers (via variables d'env)
const dbg = (...a) => { if (process.env.WSM_DEBUG === '1') console.log('[DB]', ...a); };

// Fonction d'initialisation de la base de données
async function initializeDatabase() {
  const database = await getDb();

  // Log des requêtes SQL si demandé
  if (process.env.WSM_DEBUG_SQL === '1') {
    const _prepare = database.prepare.bind(database);
    database.prepare = (sql) => {
      console.log('[SQL]', sql.trim().replace(/\s+/g, ' '));
      return _prepare(sql);
    };
  }

  // Création des tables si absentes
  await database.exec(`
    CREATE TABLE IF NOT EXISTS families (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT UNIQUE NOT NULL
    );
    CREATE TABLE IF NOT EXISTS screens (
      ref TEXT PRIMARY KEY,
      family_id INTEGER,
      family TEXT,
      content TEXT,
      width INTEGER,
      height INTEGER,
      created_at TEXT DEFAULT (datetime('now')),
      updated_at TEXT DEFAULT (datetime('now')),
      FOREIGN KEY (family_id) REFERENCES families(id)
    );
    CREATE TRIGGER IF NOT EXISTS trg_screens_updated_at
    AFTER UPDATE ON screens
    FOR EACH ROW BEGIN
      UPDATE screens SET updated_at = datetime('now') WHERE ref = OLD.ref;
    END;
  `);

  console.log('[Store] Database initialized successfully');
}

// Familles
export async function listFamilies() {
  const database = await getDb();
  return await database.all(`
    SELECT f.id, f.name,
           (SELECT COUNT(*) FROM screens s WHERE s.family_id = f.id) AS screen_count
    FROM families f
    ORDER BY f.name COLLATE NOCASE
  `);
}

export async function createFamily(name) {
  const n = String(name || '').trim();
  if (!n) throw new Error('FAMILY_NAME_REQUIRED');
  try {
    const database = await getDb();
    await database.run(`INSERT INTO families (name) VALUES (?)`, n);
  } catch (e) {
    if (String(e.message).includes('UNIQUE')) throw new Error('FAMILY_EXISTS');
    throw e;
  }
}

export async function deleteFamily(id) {
  const database = await getDb();
  const row = await database.get(`SELECT COUNT(*) AS c FROM screens WHERE family_id = ?`, id);
  if (row?.c > 0) throw new Error('FAMILY_IN_USE');
  const info = await database.run(`DELETE FROM families WHERE id = ?`, id);
  if (info.changes === 0) throw new Error('FAMILY_NOT_FOUND');
}

export async function setScreenFamily(ref, familyId) {
  // Met aussi à jour la colonne legacy 'family' pour rester cohérent
  const database = await getDb();
  const fam = familyId ? await database.get(`SELECT name FROM families WHERE id = ?`, familyId) : null;
  const info = await database.run(`UPDATE screens SET family_id = ?, family = ? WHERE ref = ?`,
    familyId ?? null, fam?.name ?? null, ref);
  if (info.changes === 0) throw new Error('SCREEN_NOT_FOUND');
}

// Écrans (rejoint avec familles)
export async function listScreens() {
  const database = await getDb();
  return await database.all(`
    SELECT s.ref, s.content, s.width, s.height, s.family_id, f.name AS family
    FROM screens s
    LEFT JOIN families f ON f.id = s.family_id
    ORDER BY s.ref COLLATE NOCASE
  `);
}

export async function listScreensByFamily() {
  const database = await getDb();
  const rows = await database.all(`
    SELECT f.id AS family_id, f.name AS family, s.ref, s.content, s.width, s.height
    FROM families f
    LEFT JOIN screens s ON s.family_id = f.id
    UNION ALL
    SELECT NULL AS family_id, 'Sans famille' AS family, s.ref, s.content, s.width, s.height
    FROM screens s
    WHERE s.family_id IS NULL
    ORDER BY family COLLATE NOCASE, ref COLLATE NOCASE
  `);

  const grouped = new Map();
  for (const r of rows) {
    const key = r.family_id == null ? 'NULL' : String(r.family_id);
    if (!grouped.has(key)) grouped.set(key, { family_id: r.family_id, family: r.family, screens: [] });
    if (r.ref) grouped.get(key).screens.push({
      ref: r.ref, content: r.content, width: r.width, height: r.height, family_id: r.family_id, family: r.family
    });
  }
  return Array.from(grouped.values());
}

export async function createScreen({ ref, familyId = null, width = null, height = null }) {
  const r = String(ref || '').trim();
  if (!r) throw new Error('REF_REQUIRED');
  const database = await getDb();
  const famName = familyId ? (await database.get(`SELECT name FROM families WHERE id = ?`, familyId))?.name ?? null : null;
  try {
    await database.run(
      `INSERT INTO screens (ref, family_id, family, width, height) VALUES (?, ?, ?, ?, ?)`,
      r, familyId ?? null, famName, width ?? null, height ?? null
    );
  } catch (e) {
    if (String(e.message).includes('UNIQUE')) throw new Error('SCREEN_EXISTS');
    throw e;
  }
}

export async function setDimensions(ref, width, height) {
  const w = (width === '' || width == null) ? null : Number(width);
  const h = (height === '' || height == null) ? null : Number(height);
  const database = await getDb();
  const info = await database.run(`UPDATE screens SET width = ?, height = ? WHERE ref = ?`, w, h, ref);
  if (info.changes === 0) throw new Error('SCREEN_NOT_FOUND');
}

// Librairie (fichiers)
export function listLibraryFiles() {
  const allowed = /\.(png|jpe?g|gif|webp|bmp|tiff|svg)$/i;
  return fs.readdirSync(libraryDir, { withFileTypes: true })
    .filter(d => d.isFile() && allowed.test(d.name))
    .map(d => d.name)
    .sort((a, b) => a.localeCompare(b, 'fr', { sensitivity: 'base' }));
}

export async function deleteLibraryFile(fileName) {
  if (!fileName || /[\\/]/.test(fileName)) throw new Error('BAD_PATH');
  const database = await getDb();
  // Bloquer si le contenu est assigné à un écran
  const row = await database.get('SELECT COUNT(*) AS c FROM screens WHERE content = ?', fileName);
  if (row?.c > 0) throw new Error('FILE_IN_USE');

  const abs = path.resolve(libraryDir, fileName);
  const safeRoot = path.resolve(libraryDir);
  if (!abs.startsWith(safeRoot)) throw new Error('BAD_PATH');
  if (!fs.existsSync(abs)) throw new Error('FILE_NOT_FOUND');

  fs.unlinkSync(abs);
  dbg('deleteLibraryFile', { fileName });
}

// Écrans: helpers CRUD/assignation
export async function getScreen(ref) {
  const database = await getDb();
  return await database.get(`
    SELECT s.ref, s.content, s.width, s.height, s.family_id, f.name AS family
    FROM screens s
    LEFT JOIN families f ON f.id = s.family_id
    WHERE s.ref = ?
  `, ref);
}
// export async function getScreen(ref) {
//   const stmt = db.prepare('SELECT * FROM screens WHERE ref = ?');
//   return stmt.get(ref);
// }

export async function assignContent(ref, fileName) {
  const screen = await getScreen(ref);
  if (!screen) throw new Error('SCREEN_NOT_FOUND');

  // Valider que le fichier existe dans la librairie
  const abs = path.join(libraryDir, fileName);
  if (!fs.existsSync(abs)) throw new Error('CONTENT_NOT_FOUND');

  const database = await getDb();
  await database.run('UPDATE screens SET content = ? WHERE ref = ?', fileName, ref);
  dbg('assignContent', { ref, fileName });
}

export async function unassignContent(ref) {
  const database = await getDb();
  const info = await database.run('UPDATE screens SET content = NULL WHERE ref = ?', ref);
  if (info.changes === 0) throw new Error('SCREEN_NOT_FOUND');
  dbg('unassignContent', { ref });
}

export async function deleteScreen(ref) {
  const database = await getDb();
  const info = await database.run('DELETE FROM screens WHERE ref = ?', ref);
  if (info.changes === 0) throw new Error('SCREEN_NOT_FOUND');
  dbg('deleteScreen', { ref });
}

// Export / import (familles rapprochées par nom, portable d'une base à l'autre)
export async function exportData() {
  const database = await getDb();
  const families = await database.all('SELECT name FROM families ORDER BY name COLLATE NOCASE');
  const screens = await database.all(`
    SELECT s.ref, f.name AS family, s.content, s.width, s.height
    FROM screens s
    LEFT JOIN families f ON f.id = s.family_id
    ORDER BY s.ref COLLATE NOCASE
  `);
  return {
    format: 'webstream-export',
    version: 1,
    exportedAt: new Date().toISOString(),
    families: families.map(f => f.name),
    screens
  };
}

const MAX_IMPORT_SCREENS = 10000;

function cleanNumber(v) {
  if (v == null || v === '') return null;
  const n = Number(v);
  return Number.isFinite(n) && n > 0 ? n : null;
}

function normalizeImport(data) {
  if (!data || !Array.isArray(data.screens)) throw new Error('IMPORT_INVALID');
  if (data.screens.length > MAX_IMPORT_SCREENS) throw new Error('IMPORT_TOO_LARGE');

  const families = new Set();
  for (const f of Array.isArray(data.families) ? data.families : []) {
    const name = String(f ?? '').trim();
    if (name) families.add(name);
  }

  const screens = new Map();
  for (const s of data.screens) {
    const ref = String(s?.ref ?? '').trim();
    if (!ref) continue;
    const family = String(s.family ?? '').trim() || null;
    if (family) families.add(family);
    const content = String(s.content ?? '').trim();
    screens.set(ref, {
      ref,
      family,
      content: content && !/[\\/]/.test(content) ? content : null,
      width: cleanNumber(s.width),
      height: cleanNumber(s.height)
    });
  }
  return { families: [...families], screens: [...screens.values()] };
}

// mode : 'skip' (ajouter seulement les nouveaux), 'overwrite' (mettre à jour les existants), 'replace' (tout remplacer)
export async function importData(data, mode = 'skip') {
  if (!['skip', 'overwrite', 'replace'].includes(mode)) throw new Error('IMPORT_BAD_MODE');
  const { families, screens } = normalizeImport(data);
  const database = await getDb();
  const result = { familiesAdded: 0, screensAdded: 0, screensUpdated: 0, screensSkipped: 0, missingFiles: [] };

  await database.exec('BEGIN IMMEDIATE');
  try {
    if (mode === 'replace') {
      await database.run('DELETE FROM screens');
      await database.run('DELETE FROM families');
    }

    for (const name of families) {
      const info = await database.run('INSERT OR IGNORE INTO families (name) VALUES (?)', name);
      result.familiesAdded += info.changes;
    }
    const idByName = new Map((await database.all('SELECT id, name FROM families')).map(f => [f.name, f.id]));

    for (const s of screens) {
      const familyId = s.family ? idByName.get(s.family) ?? null : null;
      const exists = await database.get('SELECT 1 AS x FROM screens WHERE ref = ?', s.ref);
      if (!exists) {
        await database.run(
          'INSERT INTO screens (ref, family_id, family, content, width, height) VALUES (?, ?, ?, ?, ?, ?)',
          s.ref, familyId, s.family, s.content, s.width, s.height
        );
        result.screensAdded++;
      } else if (mode === 'overwrite') {
        await database.run(
          'UPDATE screens SET family_id = ?, family = ?, content = ?, width = ?, height = ? WHERE ref = ?',
          familyId, s.family, s.content, s.width, s.height, s.ref
        );
        result.screensUpdated++;
      } else {
        result.screensSkipped++;
      }
      if (s.content && !resolveContentPath(s.content) && !result.missingFiles.includes(s.content)) {
        result.missingFiles.push(s.content);
      }
    }
    await database.exec('COMMIT');
  } catch (e) {
    await database.exec('ROLLBACK');
    throw e;
  }
  return result;
}

// Lit une base SQLite d'une ancienne version (main ou mod) et la convertit au format d'import
export async function readSqliteExport(filePath) {
  let src;
  try {
    src = await open({ filename: filePath, driver: sqlite3.Database });
    const tables = new Set((await src.all(`SELECT name FROM sqlite_master WHERE type = 'table'`)).map(r => r.name));
    if (!tables.has('screens')) throw new Error('IMPORT_NO_SCREENS_TABLE');

    const famById = new Map();
    const families = [];
    if (tables.has('families')) {
      for (const f of await src.all('SELECT id, name FROM families')) {
        famById.set(f.id, f.name);
        families.push(f.name);
      }
    }

    const rows = await src.all('SELECT * FROM screens');
    const screens = rows.map(r => ({
      ref: r.ref,
      family: (r.family_id != null ? famById.get(r.family_id) : null) ?? r.family ?? null,
      content: r.content,
      width: r.width,
      height: r.height
    }));
    return { families, screens };
  } catch (e) {
    if (String(e.code).startsWith('SQLITE_')) throw new Error('IMPORT_NOT_A_DATABASE');
    throw e;
  } finally {
    if (src) await src.close();
  }
}

export function resolveContentPath(fileName) {
  if (!fileName) return null;
  const abs = path.join(libraryDir, fileName);
  return fs.existsSync(abs) ? abs : null;
}
