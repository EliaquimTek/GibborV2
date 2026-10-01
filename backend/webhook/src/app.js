const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');

const webhookRoutes = require('./presentation/http/routes/webhook');

const app = express();

// Middleware
app.use(helmet());
app.use(cors());
app.use(morgan('combined'));
app.use(bodyParser.json({ limit: '10mb' })); // Increase limit for media files
app.use(bodyParser.urlencoded({ extended: true, limit: '10mb' }));

// Routes
app.use('/webhook', webhookRoutes);

// Health check endpoint
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'OK', timestamp: new Date().toISOString() });
});

// 404 handler (Express 5 ya no acepta '*' como ruta; un middleware sin ruta es equivalente)
app.use((req, res) => {
  res.status(404).json({ error: 'Route not found' });
});

// Error handling middleware
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({ error: 'Something went wrong!' });
});

module.exports = app;
