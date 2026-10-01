const { PORT } = require('./config/env');
const app = require('./app');

// Start server
const server = app.listen(PORT, () => {
  console.log(`GIBBOR Security Server running on port ${PORT}`);
});

module.exports = { app, server };
