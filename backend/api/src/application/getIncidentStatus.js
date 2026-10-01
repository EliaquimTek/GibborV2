const { mapTransaction } = require("../domain/incident");
const walletRepository = require("../infrastructure/crossmint/walletRepository");

/**
 * Caso de uso: polling del estado de una transacción.
 */
async function getIncidentStatus(email, txId) {
  const txResult = await walletRepository.getTransaction(email, txId);
  const { status, txHash, explorerLink } = mapTransaction(txResult, txId);

  return { success: true, status, txId, txHash, explorerLink, raw: txResult };
}

module.exports = { getIncidentStatus };
