const { hasRequiredEvidenceFields } = require("../../../domain/incident");
const { attachEvidence } = require("../../../application/attachEvidence");

// POST /api/incident/:incidentId/evidence   (también POST /api/evidence con incidentId en el body)
async function postEvidence(req, res) {
  try {
    const incidentId = req.params.incidentId || req.body.incidentId;
    const { email, mediaType, mediaHash } = req.body;

    if (!hasRequiredEvidenceFields({ email, incidentId, mediaType, mediaHash })) {
      return res.status(400).json({
        success: false,
        error: "Campos requeridos: email, incidentId, mediaType (audio|video), mediaHash",
      });
    }

    const result = await attachEvidence({ email, incidentId, mediaType, mediaHash });
    return res.status(result.success ? 200 : 502).json(result);
  } catch (err) {
    console.error("❌ Error en evidencia:", err.message);
    return res.status(err.status || 500).json({
      success: false,
      error: err.message,
      details: err.body || null,
    });
  }
}

module.exports = { postEvidence };
