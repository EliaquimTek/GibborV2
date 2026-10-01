const logger = require('../../../shared/logger');
const { hasRequiredPanicAlertFields } = require('../../../domain/panicAlert');
const { processPanicAlert } = require('../../../application/processPanicAlert');

/**
 * Process incoming PanicAlert events from Goldsky
 * @param {Object} req - Express request object
 * @param {Object} res - Express response object
 */
async function handlePanicAlert(req, res) {
  try {
    const { evidence_hash, location, user_id, timestamp, transaction_hash } = req.body;

    logger.info(`Received PanicAlert for user ${user_id} with evidence hash ${evidence_hash}`);

    // Validate required fields
    if (!hasRequiredPanicAlertFields({ evidence_hash, location, user_id })) {
      return res.status(400).json({
        error: 'Missing required fields: evidence_hash, location, user_id'
      });
    }

    await processPanicAlert({ evidence_hash, location, user_id, timestamp, transaction_hash });

    logger.info(`Emergency response initiated for user ${user_id}`);

    res.status(200).json({
      status: 'success',
      message: 'Panic alert processed successfully',
      timestamp: new Date().toISOString()
    });
  } catch (error) {
    logger.error(`Error processing PanicAlert: ${error.message}`);
    res.status(500).json({
      error: 'Internal server error',
      details: process.env.NODE_ENV === 'development' ? error.message : undefined
    });
  }
}

module.exports = {
  handlePanicAlert
};
