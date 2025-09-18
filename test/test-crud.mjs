import Database from 'better-sqlite3';

const db = new Database('src/db/webstream.db');

try {
  // Test 1: Lister les écrans existants
  console.log('=== ÉCRANS EXISTANTS ===');
  const screens = db.prepare('SELECT * FROM screens').all();
  console.log(screens);

  // Test 2: Créer un écran test (si pas déjà existant)
  const testRef = 'test_overlay_' + Date.now();
  console.log('\n=== CRÉATION ÉCRAN TEST ===');
  const insert = db.prepare('INSERT INTO screens (ref, width, height, family_id) VALUES (?, ?, ?, ?)');
  const result = insert.run(testRef, 1.5, 2.8, 1);
  console.log('Insert result:', result);

  // Test 3: Vérifier l'insertion
  const check = db.prepare('SELECT * FROM screens WHERE ref = ?').get(testRef);
  console.log('Écran créé:', check);

  // Test 4: Modifier dimensions
  console.log('\n=== MODIFICATION DIMENSIONS ===');
  const updateDim = db.prepare('UPDATE screens SET width = ?, height = ? WHERE ref = ?');
  const updateResult = updateDim.run(3.7, 4.2, testRef);
  console.log('Update dimensions result:', updateResult);

  // Test 5: Modifier famille
  console.log('\n=== MODIFICATION FAMILLE ===');
  const updateFam = db.prepare('UPDATE screens SET family_id = ? WHERE ref = ?');
  const updateFamResult = updateFam.run(2, testRef);
  console.log('Update famille result:', updateFamResult);

  // Test 6: Vérifier les modifications
  const final = db.prepare('SELECT * FROM screens WHERE ref = ?').get(testRef);
  console.log('Écran après modifs:', final);

  // Test 7: Lister les familles
  console.log('\n=== FAMILLES ===');
  const families = db.prepare('SELECT * FROM families').all();
  console.log(families);

} catch (e) {
  console.error('Erreur:', e);
} finally {
  db.close();
}