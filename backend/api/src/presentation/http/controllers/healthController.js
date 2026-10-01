const { STELLAR_CONTRACT_ID } = require("../../../config/env");

function health(req, res) {
  res.json({ status: "ok", contractId: STELLAR_CONTRACT_ID });
}

module.exports = { health };
