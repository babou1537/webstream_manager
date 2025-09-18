import Database from 'better-sqlite3';

const db = new Database('src/db/webstream.db');
try {
  console.log('Tables:', db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all());
  console.log('Screens columns:', db.prepare('PRAGMA table_info(screens)').all());
  
  const familiesExists = db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='families'").get();
  if (familiesExists) {
    console.log('Families count:', db.prepare('SELECT COUNT(*) as c FROM families').get());
  } else {
    console.log('Table families does NOT exist');
  }
} catch (e) {
  console.error('Error:', e.message);
} finally {
  db.close();
}