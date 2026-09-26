import crypto from 'crypto';

const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || '';

function isLoopback(req) {
  const addr = req.socket.remoteAddress || '';
  return addr === '127.0.0.1' || addr === '::1' || addr === '::ffff:127.0.0.1';
}

// Images d'écrans, contenu et fichiers statiques : lisibles depuis le réseau
function isPublicAsset(req) {
  if (req.method !== 'GET' && req.method !== 'HEAD') return false;
  return /^\/[^/]+\.png$/.test(req.path) || req.path.startsWith('/content/') || req.path.startsWith('/static/');
}

function passwordMatches(header) {
  const m = /^Basic (.+)$/i.exec(header || '');
  if (!m) return false;
  const decoded = Buffer.from(m[1], 'base64').toString('utf8');
  const given = decoded.slice(decoded.indexOf(':') + 1);
  const a = crypto.createHash('sha256').update(given).digest();
  const b = crypto.createHash('sha256').update(ADMIN_PASSWORD).digest();
  return crypto.timingSafeEqual(a, b);
}

/**
 * Protège l'interface d'administration :
 * accès libre (et administrateur) depuis cette machine, mot de passe (ADMIN_PASSWORD) depuis ailleurs.
 * Derrière un proxy inverse local, toutes les requêtes semblent locales.
 */
export function networkGuard(req, res, next) {
  if (isLoopback(req)) {
    req.isAdmin = true;
    return next();
  }
  if (isPublicAsset(req)) return next();

  if (!ADMIN_PASSWORD) {
    return res.status(403).send('Accès distant désactivé : définissez adminPassword (mod) ou ADMIN_PASSWORD (serveur).');
  }
  if (passwordMatches(req.headers.authorization)) {
    req.isAdmin = true;
    return next();
  }

  res.set('WWW-Authenticate', 'Basic realm="WebStream Manager", charset="UTF-8"');
  return res.status(401).send('Authentification requise');
}

// Refuse les écritures qui n'ont pas passé networkGuard
export function requireWrite(req, res, next) {
  if (req.isAdmin) return next();
  return res.status(403).json({
    error: 'PERMISSION_DENIED',
    message: 'Accès en écriture refusé.'
  });
}

// Informations de permission pour les vues
export function injectPermissions(req, res, next) {
  res.locals.isAdmin = req.isAdmin === true;
  res.locals.username = String(req.query.user || 'guest').toLowerCase();
  next();
}
