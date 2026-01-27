import express from 'express';
const router = express.Router();

router.get('/', (req, res) => {
  res.render('layout', {
    title: 'Accueil',
    page: 'home',
    view: 'partials/home'
  });
});

export default router;
