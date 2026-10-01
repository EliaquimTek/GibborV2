const express = require('express');
const router = express.Router();
const { handlePanicAlert } = require('../controllers/webhookController');
const { validateGoldskySignature } = require('../middleware/goldskySignature');

// Webhook endpoint for Goldsky events
router.post('/goldsky', validateGoldskySignature, handlePanicAlert);

module.exports = router;
