const { hasRequiredIncidentFields } = require("../../../domain/incident");
const { createIncident } = require("../../../application/createIncident");
const { getIncidentStatus } = require("../../../application/getIncidentStatus");

function sendError(res, err) {
  const status = err.status || 500;
  return res.status(status).json({
    success: false,
    error: err.message,
    details: err.body || null,
  });
}

// POST /api/incident
async function postIncident(req, res) {
  try {
    const { email, incidentId, timestamp, latE7, lonE7, initialHash } = req.body;

    // Validación básica
    if (!hasRequiredIncidentFields({ email, incidentId, timestamp, latE7, lonE7, initialHash })) {
      return res.status(400).json({
        success: false,
        error: "Campos requeridos: email, incidentId, timestamp, latE7, lonE7, initialHash",
      });
    }

    const result = await createIncident({ email, incidentId, timestamp, latE7, lonE7, initialHash });
    return res.json(result);
  } catch (err) {
    console.error("❌ Error en /api/incident:", err.message);
    return sendError(res, err);
  }
}

// GET /api/incident/:txId
async function getIncident(req, res) {
  try {
    const { txId } = req.params;
    const { email } = req.query;

    if (!email || !txId) {
      return res.status(400).json({
        success: false,
        error: "Parámetros requeridos: txId (path), email (query)",
      });
    }

    const result = await getIncidentStatus(email, txId);
    return res.json(result);
  } catch (err) {
    console.error("❌ Error en GET /api/incident:", err.message);
    return sendError(res, err);
  }
}

module.exports = { postIncident, getIncident };
