// In a real implementation, you would use an SMS provider like Twilio, AWS SNS, etc.
// For this example, we'll create a mock service that logs the SMS sending
const logger = require('../shared/logger');
const { buildEmergencyMessage } = require('../domain/panicAlert');

/**
 * Send emergency SMS to preconfigured contacts
 * @param {Object} params - SMS parameters
 * @param {string} params.user_id - User ID
 * @param {string} params.location - Location of the emergency
 * @param {string} params.evidence_hash - Hash of the evidence on blockchain
 * @param {string} params.timestamp - Timestamp of the event
 */
async function sendEmergencySMS(params) {
  try {
    // In production, integrate with SMS provider (Twilio, AWS SNS, etc.)
    // Example with Twilio:
    /*
    const accountSid = process.env.TWILIO_ACCOUNT_SID;
    const authToken = process.env.TWILIO_AUTH_TOKEN;
    const client = require('twilio')(accountSid, authToken);

    const message = await client.messages.create({
      body: buildEmergencyMessage(params),
      from: process.env.TWILIO_PHONE_NUMBER,
      to: process.env.EMERGENCY_CONTACT_NUMBER // This would be a list of numbers
    });
    */

    // Mock implementation for development
    const message = buildEmergencyMessage(params);

    logger.info(`SMS sent: ${message}`);
    // In a real system, you would return the message SID or confirmation
    return {
      success: true,
      messageId: 'mock-sms-id-' + Date.now(),
      message: message
    };
  } catch (error) {
    logger.error(`Failed to send emergency SMS: ${error.message}`);
    throw error;
  }
}

module.exports = {
  sendEmergencySMS
};
