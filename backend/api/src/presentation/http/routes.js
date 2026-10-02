const express = require("express");
const { postIncident, getIncident } = require("./controllers/incidentController");
const { postEvidence } = require("./controllers/evidenceController");
const { health } = require("./controllers/healthController");

const router = express.Router();

router.post("/api/incident", postIncident);
router.get("/api/incident/:txId", getIncident);
router.post("/api/incident/:incidentId/evidence", postEvidence);
router.post("/api/evidence", postEvidence);
router.get("/health", health);

module.exports = router;
