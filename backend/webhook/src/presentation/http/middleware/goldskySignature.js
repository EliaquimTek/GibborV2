const crypto = require('crypto');

/**
 * Validate that incoming requests come from Goldsky
 * @param {Object} req - Express request object
 * @param {Object} res - Express response object
 * @param {Function} next - Next middleware function
 */
function validateGoldskySignature(req, res, next) {
  try {
    // In production, you would verify a signature or secret token
    // For now, we'll check for a simple header or implement HMAC validation
    // Example implementation using a shared secret (to be configured)
    const signature = req.headers['x-goldsky-signature'];
    const secret = process.env.GOLDSKY_WEBHOOK_SECRET;

    if (!signature || !secret) {
      // In development, we might allow requests without validation
      // In production, this should be strictly enforced
      if (process.env.NODE_ENV !== 'production') {
        console.warn('Goldsky signature validation skipped (development mode)');
        return next();
      }
      return res.status(401).json({
        error: 'Unauthorized: Missing Goldsky signature'
      });
    }

    // Verify the signature (example using HMAC-SHA256)
    const hmac = crypto.createHmac('sha256', secret);
    hmac.update(JSON.stringify(req.body));
    const expectedSignature = hmac.digest('hex');

    // Compare signatures (using timing-safe comparison)
    const isValid = crypto.timingSafeEqual(
      Buffer.from(signature, 'hex'),
      Buffer.from(expectedSignature, 'hex')
    );

    if (!isValid) {
      return res.status(401).json({
        error: 'Unauthorized: Invalid Goldsky signature'
      });
    }

    next();
  } catch (error) {
    console.error('Error validating Goldsky signature:', error);
    return res.status(500).json({
      error: 'Internal server error during validation'
    });
  }
}

module.exports = {
  validateGoldskySignature
};
