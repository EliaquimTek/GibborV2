const dotenv = require('dotenv');

// Load environment variables
dotenv.config();

module.exports = {
  PORT: process.env.PORT || 3000,
};
