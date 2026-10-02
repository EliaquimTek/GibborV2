const { attachMediaCall } = require("../domain/incident");
const { getOrCreateUserWallet, callContract } = require("../infrastructure/crossmint/stellarWalletGateway");

/**
 * Caso de uso: ancla el SHA-256 de un archivo de evidencia en el contrato
 * (attach_media_hash). El SDK espera la confirmación on-chain.
 */
async function attachEvidence({ email, incidentId, mediaType, mediaHash }) {
  console.log(`\n📎 Evidencia [${mediaType}] para ${incidentId}: ${mediaHash}`);

  const wallet = await getOrCreateUserWallet(email);
  const uploadedAt = Math.floor(Date.now() / 1000);
  const tx = await callContract(wallet, attachMediaCall({ incidentId, mediaType, mediaHash, uploadedAt }));

  console.log(`✅ Evidencia anclada: id=${tx.transactionId}`);

  return {
    success: true,
    status: "success",
    txId: tx.transactionId,
    txHash: tx.hash,
    explorerLink: tx.explorerLink,
  };
}

module.exports = { attachEvidence };
