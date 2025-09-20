import path from 'path';
import fs from 'fs';
import Database from 'better-sqlite3';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const dbPath = path.join(__dirname, 'db', 'webstream.db');
const libraryDir = path.resolve(__dirname, '..', 'storage', 'library');

fs.mkdirSync(path.dirname(dbPath), { recursive: true });
fs.mkdirSync(libraryDir, { recursive: true });

const db = new Database(dbPath);

// Debug helpers (via variables d'env)
const dbg = (...a) => { if (process.env.WSM_DEBUG === '1') console.log('[DB]', ...a); };
// Log des requêtes SQL si demandé
if (process.env.WSM_DEBUG_SQL === '1') {
  const _prepare = db.prepare.bind(db);
  db.prepare = (sql) => {
    console.log('[SQL]', sql.trim().replace(/\s+/g, ' '));
    return _prepare(sql);
  };
}

// Création des tables si absentes
db.exec(`
CREATE TABLE IF NOT EXISTS screens (
  ref TEXT PRIMARY KEY,
  family TEXT,
  content TEXT,
  width INTEGER,
  height INTEGER,
  created_at TEXT DEFAULT (datetime('now')),
  updated_at TEXT DEFAULT (datetime('now'))
);
CREATE TRIGGER IF NOT EXISTS trg_screens_updated_at
AFTER UPDATE ON screens
FOR EACH ROW BEGIN
  UPDATE screens SET updated_at = datetime('now') WHERE ref = OLD.ref;
END;
`);

// Familles
export function listFamilies() {
  return db.prepare(`
    SELECT f.id, f.name,
           (SELECT COUNT(*) FROM screens s WHERE s.family_id = f.id) AS screen_count
    FROM families f
    ORDER BY f.name COLLATE NOCASE
  `).all();
}

export function createFamily(name) {
  const n = String(name || '').trim();
  if (!n) throw new Error('FAMILY_NAME_REQUIRED');
  try {
    db.prepare(`INSERT INTO families (name) VALUES (?)`).run(n);
  } catch (e) {
    if (String(e.message).includes('UNIQUE')) throw new Error('FAMILY_EXISTS');
    throw e;
  }
}

export function deleteFamily(id) {
  const row = db.prepare(`SELECT COUNT(*) AS c FROM screens WHERE family_id = ?`).get(id);
  if (row?.c > 0) throw new Error('FAMILY_IN_USE');
  const info = db.prepare(`DELETE FROM families WHERE id = ?`).run(id);
  if (info.changes === 0) throw new Error('FAMILY_NOT_FOUND');
}

export function setScreenFamily(ref, familyId) {
  // Met aussi à jour la colonne legacy 'family' pour rester cohérent
  const fam = familyId ? db.prepare(`SELECT name FROM families WHERE id = ?`).get(familyId) : null;
  const info = db.prepare(`UPDATE screens SET family_id = ?, family = ? WHERE ref = ?`)
    .run(familyId ?? null, fam?.name ?? null, ref);
  if (info.changes === 0) throw new Error('SCREEN_NOT_FOUND');
}

// Écrans (rejoint avec familles)
export function listScreens() {
  return db.prepare(`
    SELECT s.ref, s.content, s.width, s.height, s.family_id, f.name AS family
    FROM screens s
    LEFT JOIN families f ON f.id = s.family_id
    ORDER BY s.ref COLLATE NOCASE
  `).all();
}

export function listScreensByFamily() {
  const rows = db.prepare(`
    SELECT f.id AS family_id, f.name AS family, s.ref, s.content, s.width, s.height
    FROM families f
    LEFT JOIN screens s ON s.family_id = f.id
    UNION ALL
    SELECT NULL AS family_id, 'Sans famille' AS family, s.ref, s.content, s.width, s.height
    FROM screens s
    WHERE s.family_id IS NULL
    ORDER BY family COLLATE NOCASE, ref COLLATE NOCASE
  `).all();

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

export function createScreen({ ref, familyId = null, width = null, height = null }) {
  const r = String(ref || '').trim();
  if (!r) throw new Error('REF_REQUIRED');
  const famName = familyId ? db.prepare(`SELECT name FROM families WHERE id = ?`).get(familyId)?.name ?? null : null;
  try {
    db.prepare(`
      INSERT INTO screens (ref, family_id, family, width, height) VALUES (?, ?, ?, ?, ?)
    `).run(r, familyId ?? null, famName, width ?? null, height ?? null);
  } catch (e) {
    if (String(e.message).includes('UNIQUE')) throw new Error('SCREEN_EXISTS');
    throw e;
  }
}

export function setDimensions(ref, width, height) {
  const w = (width === '' || width == null) ? null : Number(width);
  const h = (height === '' || height == null) ? null : Number(height);
  const info = db.prepare(`UPDATE screens SET width = ?, height = ? WHERE ref = ?`).run(w, h, ref);
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

export function deleteLibraryFile(fileName) {
  if (!fileName || /[\\/]/.test(fileName)) throw new Error('BAD_PATH');

  // Bloquer si le contenu est assigné à un écran
  const row = db.prepare('SELECT COUNT(*) AS c FROM screens WHERE content = ?').get(fileName);
  if (row?.c > 0) throw new Error('FILE_IN_USE');

  const abs = path.resolve(libraryDir, fileName);
  const safeRoot = path.resolve(libraryDir);
  if (!abs.startsWith(safeRoot)) throw new Error('BAD_PATH');
  if (!fs.existsSync(abs)) throw new Error('FILE_NOT_FOUND');

  fs.unlinkSync(abs);
  dbg('deleteLibraryFile', { fileName });
}

// Écrans: helpers CRUD/assignation
export function getScreen(ref) {
  return db.prepare(`
    SELECT s.ref, s.content, s.width, s.height, s.family_id, f.name AS family
    FROM screens s
    LEFT JOIN families f ON f.id = s.family_id
    WHERE s.ref = ?
  `).get(ref);
}
// export async function getScreen(ref) {
//   const stmt = db.prepare('SELECT * FROM screens WHERE ref = ?');
//   return stmt.get(ref);
// }

export function assignContent(ref, fileName) {
  const screen = getScreen(ref);
  if (!screen) throw new Error('SCREEN_NOT_FOUND');

  // Valider que le fichier existe dans la librairie
  const abs = path.join(libraryDir, fileName);
  if (!fs.existsSync(abs)) throw new Error('CONTENT_NOT_FOUND');

  db.prepare('UPDATE screens SET content = ? WHERE ref = ?').run(fileName, ref);
  dbg('assignContent', { ref, fileName });
}

export function unassignContent(ref) {
  const info = db.prepare('UPDATE screens SET content = NULL WHERE ref = ?').run(ref);
  if (info.changes === 0) throw new Error('SCREEN_NOT_FOUND');
  dbg('unassignContent', { ref });
}

export function deleteScreen(ref) {
  const info = db.prepare('DELETE FROM screens WHERE ref = ?').run(ref);
  if (info.changes === 0) throw new Error('SCREEN_NOT_FOUND');
  dbg('deleteScreen', { ref });
}

export function resolveContentPath(fileName) {
  if (!fileName) return null;
  const abs = path.join(libraryDir, fileName);
  return fs.existsSync(abs) ? abs : null;
}
