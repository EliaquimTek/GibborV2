const logger = require('../../../shared/logger');
const { hasRequiredPanicAlertFields, subjectOf } = require('../../../domain/panicAlert');
const { processPanicAlert } = require('../../../application/processPanicAlert');

/**
 * Process incoming contract events ("created" / "media") from Goldsky
 * @param {Object} req - Express request object
 * @param {Object} res - Express response object
 */
async function handlePanicAlert(req, res) {
  try {
    const { evidence_hash, location, user_id, incident_id, event_type, media_type, timestamp, transaction_hash } = req.body;
    const alert = { evidence_hash, location, user_id, incident_id, event_type, media_type, timestamp, transaction_hash };

    logger.info(`Received ${event_type || 'PanicAlert'} event for ${subjectOf(alert)} with evidence hash ${evidence_hash}`);

    // Validate required fields
    if (!hasRequiredPanicAlertFields(alert)) {
      return res.status(400).json({
        error: 'Missing required fields: evidence_hash, incident_id (or user_id), location (except media events)'
      });
    }

    await processPanicAlert(alert);

    logger.info(`Emergency response initiated for ${subjectOf(alert)}`);

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
