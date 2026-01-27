-- Script d'optimisation pour la base de données WebStream
-- À exécuter périodiquement pour maintenir les performances

-- 1. Créer des index pour accélérer les requêtes
CREATE INDEX IF NOT EXISTS idx_screens_family ON screens(family_id);
CREATE INDEX IF NOT EXISTS idx_screens_content ON screens(content);
CREATE INDEX IF NOT EXISTS idx_families_name ON families(name);

-- 2. Analyser les tables pour optimiser le query planner
ANALYZE screens;
ANALYZE families;

-- 3. Récupérer l'espace disque inutilisé
VACUUM;

-- 4. Statistiques (lecture seule)
SELECT 'Nombre total d\'écrans:' AS info, COUNT(*) AS valeur FROM screens
UNION ALL
SELECT 'Nombre de familles:', COUNT(*) FROM families
UNION ALL
SELECT 'Écrans avec contenu:', COUNT(*) FROM screens WHERE content IS NOT NULL
UNION ALL
SELECT 'Écrans sans contenu:', COUNT(*) FROM screens WHERE content IS NULL;

-- 5. Vérifier l'intégrité de la base de données
PRAGMA integrity_check;

-- 6. Activer les optimisations SQLite
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA cache_size = -64000;  -- 64 MB de cache
PRAGMA temp_store = MEMORY;
PRAGMA mmap_size = 30000000000;

-- 7. Nettoyer les écrans orphelins (sans famille valide)
-- ATTENTION : Décommentez seulement si vous voulez supprimer les orphelins
-- DELETE FROM screens WHERE family_id IS NOT NULL AND family_id NOT IN (SELECT id FROM families);

-- 8. Afficher la taille de la base de données
SELECT page_count * page_size AS 'Taille DB (bytes)' FROM pragma_page_count(), pragma_page_size();

