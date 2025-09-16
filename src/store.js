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

// Création des tables si absentes
db.exec(`
CREATE TABLE IF NOT EXISTS screens (
  ref TEXT PRIMARY KEY,
  family TEXT,
  content TEXT,        -- nom de fichier dans storage/library
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

export function listScreens() {
  const stmt = db.prepare('SELECT ref, family, content, width, height FROM screens ORDER BY ref');
  return stmt.all();
}

export function getScreen(ref) {
  const stmt = db.prepare('SELECT ref, family, content, width, height FROM screens WHERE ref = ?');
  return stmt.get(ref);
}

export function createScreen({ ref, family = null, width = null, height = null }) {
  const stmt = db.prepare(`
    INSERT INTO screens (ref, family, width, height) VALUES (?, ?, ?, ?)
  `);
  try {
    stmt.run(ref, family, width, height);
  } catch (e) {
    if (String(e.message).includes('UNIQUE') || String(e.code) === 'SQLITE_CONSTRAINT_PRIMARYKEY') {
      throw new Error('SCREEN_EXISTS');
    }
    throw e;
  }
}

export function assignContent(ref, fileName) {
  const exists = getScreen(ref);
  if (!exists) throw new Error('SCREEN_NOT_FOUND');
  const abs = path.join(libraryDir, fileName);
  if (!fs.existsSync(abs)) throw new Error('CONTENT_NOT_FOUND');
  const stmt = db.prepare('UPDATE screens SET content = ? WHERE ref = ?');
  stmt.run(fileName, ref);
}

export function setDimensions(ref, width, height) {
  const exists = getScreen(ref);
  if (!exists) throw new Error('SCREEN_NOT_FOUND');
  const stmt = db.prepare('UPDATE screens SET width = ?, height = ? WHERE ref = ?');
  stmt.run(width ?? null, height ?? null, ref);
}

export function resolveContentPath(fileName) {
  if (!fileName) return null;
  const abs = path.join(libraryDir, fileName);
  return fs.existsSync(abs) ? abs : null;
}

export function listLibraryFiles() {
  const files = fs.readdirSync(libraryDir, { withFileTypes: true })
    .filter(d => d.isFile())
    .map(d => d.name)
    .filter(n => /\.(png|jpe?g|gif|webp)$/i.test(n))
    .sort((a, b) => a.localeCompare(b, 'fr'));
  return files;
}

export function unassignContent(ref) {
  const exists = getScreen(ref);
  if (!exists) throw new Error('SCREEN_NOT_FOUND');
  const stmt = db.prepare('UPDATE screens SET content = NULL WHERE ref = ?');
  stmt.run(ref);
}

export function deleteScreen(ref) {
  const stmt = db.prepare('DELETE FROM screens WHERE ref = ?');
  const info = stmt.run(ref);
  if (info.changes === 0) throw new Error('SCREEN_NOT_FOUND');
}
