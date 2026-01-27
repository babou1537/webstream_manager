import fs from 'fs';
import path from 'path';
import Database from 'better-sqlite3';
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
      console.log(`Adding column: ${name}`);
      db.exec(`ALTER TABLE screens ADD COLUMN ${name} ${decl}`);
    }
  };

  addCol('family', 'TEXT');
  addCol('content', 'TEXT');
  addCol('width', 'INTEGER');
  addCol('height', 'INTEGER');
  addCol('created_at', 'TEXT');
}

function ensureScreensFamilies() {
  // Table families
  db.exec(`
    CREATE TABLE IF NOT EXISTS families (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT UNIQUE NOT NULL,
      created_at TEXT DEFAULT (datetime('now')),
      updated_at TEXT DEFAULT (datetime('now'))
    );
    CREATE TRIGGER IF NOT EXISTS trg_families_updated_at
    AFTER UPDATE ON families
    FOR EACH ROW BEGIN
      UPDATE families SET updated_at = datetime('now') WHERE id = OLD.id;
    END;
  `);

  // Colonne family_id sur screens
  const cols = new Set(db.prepare(`PRAGMA table_info('screens')`).all().map(r => r.name));
  if (!cols.has('family_id')) {
    console.log('Adding column: family_id');
    db.exec(`ALTER TABLE screens ADD COLUMN family_id INTEGER REFERENCES families(id)`);
  }

  // Backfill: créer familles depuis screens.family (legacy) puis pointer screens.family_id
  const hasFamilyText = cols.has('family');
  if (hasFamilyText) {
    const distinct = db.prepare(`
      SELECT DISTINCT family FROM screens
      WHERE family IS NOT NULL AND TRIM(family) <> ''
    `).all();
    const insFam = db.prepare(`INSERT OR IGNORE INTO families (name) VALUES (?)`);
    distinct.forEach(r => insFam.run(String(r.family).trim()));
    db.exec(`
      UPDATE screens
      SET family_id = (SELECT id FROM families f WHERE f.name = screens.family)
      WHERE family IS NOT NULL AND TRIM(family) <> '' AND family_id IS NULL
    `);
  }
}

try {
  db.exec('BEGIN');
  ensureColumns();
  ensureScreensFamilies();
  db.exec('COMMIT');
  console.log('Migration completed successfully');
} catch (e) {
  console.error('Migration FAILED:', e);
  db.exec('ROLLBACK');
  process.exit(1);
} finally {
  db.close();
}
