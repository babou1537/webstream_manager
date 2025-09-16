import Database from 'better-sqlite3';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const dbPath = path.join(__dirname, 'webstream.db');
fs.mkdirSync(path.dirname(dbPath), { recursive: true });

const db = new Database(dbPath);

function ensureColumns() {
  db.exec(`CREATE TABLE IF NOT EXISTS screens (ref TEXT PRIMARY KEY)`);

  const cols = new Set(db.prepare(`PRAGMA table_info('screens')`).all().map(r => r.name));
  const addCol = (name, decl) => {
    if (!cols.has(name)) {
      db.exec(`ALTER TABLE screens ADD COLUMN ${name} ${decl}`);
      cols.add(name);
    }
  };

  addCol('family', 'TEXT');
  addCol('content', 'TEXT');   // nom de fichier dans storage/library
  addCol('width', 'INTEGER');
  addCol('height', 'INTEGER');
  addCol('created_at', 'TEXT');
  addCol('updated_at', 'TEXT');

  // Initialiser les timestamps si NULL
  db.exec(`UPDATE screens SET created_at = COALESCE(created_at, datetime('now'))`);
  db.exec(`UPDATE screens SET updated_at = COALESCE(updated_at, datetime('now'))`);

  // Déclencheur pour updated_at
  db.exec(`
    CREATE TRIGGER IF NOT EXISTS trg_screens_updated_at
    AFTER UPDATE ON screens
    FOR EACH ROW BEGIN
      UPDATE screens SET updated_at = datetime('now') WHERE ref = OLD.ref;
    END;
  `);
}

try {
  db.exec('BEGIN');
  ensureColumns();
  db.exec('COMMIT');
  console.log('Migration OK:', dbPath);
} catch (e) {
  db.exec('ROLLBACK');
  console.error('Migration FAILED:', e);
  process.exitCode = 1;
} finally {
  db.close();
}
