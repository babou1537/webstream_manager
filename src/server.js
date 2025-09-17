import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';

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
