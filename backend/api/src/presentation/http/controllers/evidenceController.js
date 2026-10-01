// POST /api/evidence (FASE 2 — placeholder)
async function postEvidence(req, res) {
  // TODO FASE 2: recibir hash de evidencia + incident_id, anclar en blockchain
  return res.status(501).json({
    success: false,
    error: "Endpoint aún no implementado (FASE 2)",
  });
}

module.exports = { postEvidence };
