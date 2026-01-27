import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const permissionsPath = path.join(__dirname, 'permissions.json');

// Charger les permissions
let permissions = { admins: [], readOnly: false };
if (fs.existsSync(permissionsPath)) {
  try {
    permissions = JSON.parse(fs.readFileSync(permissionsPath, 'utf-8'));
    console.log('[Permissions] Loaded:', permissions);
  } catch (error) {
    console.error('[Permissions] Error loading permissions.json:', error);
  }
}

// Middleware pour vérifier les permissions d'écriture
export function requireWrite(req, res, next) {
  // Pour l'instant, on simule un utilisateur (dans le futur, récupérer depuis Minecraft)
  const username = req.query.user || req.body.user || 'guest';

  // Vérifier si l'utilisateur est admin
  if (permissions.admins.includes(username)) {
    req.isAdmin = true;
    return next();
  }

  // Sinon, refuser l'accès
  return res.status(403).json({
    error: 'PERMISSION_DENIED',
    message: 'Seuls les administrateurs peuvent modifier le contenu.'
  });
}

// Middleware pour injecter les informations de permission dans les vues
export function injectPermissions(req, res, next) {
  const username = req.query.user || 'guest';
  res.locals.isAdmin = permissions.admins.includes(username);
  res.locals.username = username;
  next();
}

export function isAdmin(username) {
  return permissions.admins.includes(username);
}

