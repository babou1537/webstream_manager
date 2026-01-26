import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';

// Import du store
import * as store from './store.js';

// Routes
import indexRouter from './routes/index.js';
import familiesRouter from './routes/families.js';
import libraryRouter from './routes/library.js';
import screensRouter from './routes/screens.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 8282;

// View engine
app.set('views', path.join(__dirname, 'views'));
app.set('view engine', 'ejs');

// Middleware GLOBAL (essentiel pour FormData)
app.use(express.urlencoded({ extended: false }));
app.use(express.json());

// Static files
app.use('/static', express.static(path.join(__dirname, 'static')));
app.use('/content', express.static(path.resolve(__dirname, '..', 'storage', 'library')));

// Route dynamique pour servir les écrans (avant les autres routes)
app.get('/:screenRef.png', async (req, res) => {
  try {
    const screenRef = req.params.screenRef;
    console.log('[SCREEN REQUEST]', screenRef);
    
    // Récupérer l'écran depuis la DB (async)
    const screen = await store.getScreen(screenRef);
    if (!screen || !screen.content) {
      console.log('[SCREEN 404]', screenRef, 'not found or no content assigned');
      return res.status(404).send('Screen not found or no content assigned');
    }
    
    // Chemin vers le fichier de contenu
    const contentPath = path.resolve(__dirname, '..', 'storage', 'library', screen.content);
    console.log('[SCREEN SERVE]', screenRef, '->', screen.content, 'from', contentPath);
    
    // Vérifier que le fichier existe
    const fs = await import('fs');
    if (!fs.existsSync(contentPath)) {
      console.log('[CONTENT 404]', contentPath, 'file not found');
      return res.status(404).send('Content file not found');
    }
    
    // Servir le fichier
    res.sendFile(contentPath);
    
  } catch (error) {
    console.error('[SCREEN ERROR]', error);
    res.status(500).send('Server error');
  }
});

// Routes
app.use('/', indexRouter);
app.use('/families', familiesRouter);
app.use('/library', libraryRouter);
app.use('/screens', screensRouter);

// 404
app.use((req, res) => {
  res.status(404).render('404', { title: 'Page non trouvée' });
});

app.listen(PORT, () => {
  console.log(`WebStream Manager running on http://localhost:${PORT}`);
});
