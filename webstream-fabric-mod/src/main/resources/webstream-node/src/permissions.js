import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Chemin vers le fichier ops.json du serveur Minecraft
// Remonter de src/ -> webstream_manager/ -> config/ -> racine serveur/
const minecraftServerRoot = path.resolve(__dirname, '..', '..', '..');
const opsFilePath = path.join(minecraftServerRoot, 'ops.json');

let minecraftOps = [];

// Charger les OPs depuis le fichier ops.json de Minecraft
function loadMinecraftOps() {
  try {
    if (fs.existsSync(opsFilePath)) {
      const opsData = JSON.parse(fs.readFileSync(opsFilePath, 'utf-8'));
      minecraftOps = opsData
        .filter(op => op.level >= 3) // Niveau 3+ = permissions admin
        .map(op => op.name.toLowerCase());
      console.log(`[Permissions] Loaded ${minecraftOps.length} Minecraft OPs from ops.json`);
      console.log(`[Permissions] OPs:`, minecraftOps);
    } else {
      console.warn(`[Permissions] ops.json not found at: ${opsFilePath}`);
      console.warn(`[Permissions] Using fallback: all users are admins`);
    }
  } catch (error) {
    console.error('[Permissions] Error loading ops.json:', error.message);
    console.warn('[Permissions] Using fallback: all users are admins');
  }
}

// Charger les OPs au démarrage
loadMinecraftOps();

// Recharger les OPs toutes les 30 secondes (pour détecter les changements)
setInterval(loadMinecraftOps, 30000);

/**
 * Middleware pour vérifier les permissions d'écriture
 * Seuls les OPs Minecraft de niveau 3+ peuvent modifier
 */
export function requireWrite(req, res, next) {
  const username = (req.query.user || req.body.user || 'guest').toLowerCase();

  // Si ops.json n'existe pas ou est vide, tout le monde est admin (mode développement)
  if (minecraftOps.length === 0) {
    req.isAdmin = true;
    return next();
  }

  // Vérifier si l'utilisateur est OP
  if (minecraftOps.includes(username)) {
    req.isAdmin = true;
    return next();
  }

  // Sinon, refuser l'accès
  return res.status(403).json({
    error: 'PERMISSION_DENIED',
    message: 'Seuls les opérateurs Minecraft (OP niveau 3+) peuvent modifier le contenu.'
  });
}

/**
 * Middleware pour injecter les informations de permission dans les vues
 */
export function injectPermissions(req, res, next) {
  const username = (req.query.user || 'guest').toLowerCase();

  // Si ops.json n'existe pas ou est vide, tout le monde est admin
  const isAdmin = minecraftOps.length === 0 || minecraftOps.includes(username);

  res.locals.isAdmin = isAdmin;
  res.locals.username = username;
  res.locals.minecraftOpsEnabled = minecraftOps.length > 0;

  next();
}

/**
 * Vérifier si un utilisateur est admin
 */
export function isAdmin(username) {
  if (minecraftOps.length === 0) return true; // Mode dev
  return minecraftOps.includes(username.toLowerCase());
}

/**
 * Obtenir la liste des OPs actuels
 */
export function getOps() {
  return [...minecraftOps];
}

