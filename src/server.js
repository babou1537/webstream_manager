import fs from 'fs';
import path from 'path';
import express from 'express';
import { fileURLToPath } from 'url';
import { getScreen, resolveContentPath } from './store.js';
import indexRouter from './routes/index.js';
import libraryRouter from './routes/library.js';
import screensRouter from './routes/screens.js';
import morgan from 'morgan';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 8282;

app.set('views', path.join(__dirname, 'views'));
app.set('view engine', 'ejs');

app.use(express.urlencoded({ extended: true }));
app.use(express.json());
app.use('/static', express.static(path.join(__dirname, 'static')));
app.use('/content', express.static(path.resolve(__dirname, '..', 'storage', 'library')));

app.use('/', indexRouter);
app.use('/library', libraryRouter);
app.use('/screens', screensRouter);

// Logger HTTP (format court) -> active si WSM_HTTP_LOG !== '0'
if (process.env.WSM_HTTP_LOG !== '0') {
  app.use(morgan('[:date[iso]] :method :url :status :res[content-length] - :response-time ms'));
}

if (process.env.WSM_LOG_BODY === '1') {
  app.use((req, res, next) => {
    // évite de loguer l’upload binaire
    if (req.method !== 'GET' && req.originalUrl !== '/library/upload') {
      console.log('[BODY]', req.method, req.originalUrl, req.body);
    }
    next();
  });
}

// Endpoint pour Minecraft: http://[::]:8282/{ref}.png
app.get('/:ref.png', (req, res) => {
  const { ref } = req.params;
  const screen = getScreen(ref);
  if (!screen || !screen.content) return res.status(404).send('Not found');

  const abs = resolveContentPath(screen.content);
  if (!abs) return res.status(404).send('Content not found');

  // Laisser Express déterminer le Content-Type via l’extension
  res.sendFile(abs, err => {
    if (err) res.status(500).send('Error serving file');
  });
});

// Fallback 404
app.use((req, res) => {
  res.status(404).render('404', { title: 'Not Found' });
});

app.listen(PORT, () => {
  console.log(`WebStream Manager running on http://localhost:${PORT}`);
});
