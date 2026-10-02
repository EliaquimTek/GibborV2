const { createIncidentCall } = require("../domain/incident");
const { getOrCreateUserWallet, callContract } = require("../infrastructure/crossmint/stellarWalletGateway");

/**
 * Caso de uso: crea wallet (si no existe) + transacción create_incident on-chain.
 */
async function createIncident(incident) {
  const { email, incidentId, latE7, lonE7, initialHash } = incident;

  console.log(`\n══════════════════════════════════════`);
  console.log(`📡 Nuevo incidente: ${incidentId}`);
  console.log(`   email: ${email}`);
  console.log(`   lat_e7: ${latE7}, lon_e7: ${lonE7}`);
  console.log(`   hash: ${initialHash}`);
  console.log(`══════════════════════════════════════\n`);

  // 1) Asegurar que exista la wallet Stellar del usuario
  console.log("1️⃣  Creando/verificando wallet Stellar...");
  const wallet = await getOrCreateUserWallet(email);
  console.log(`   Wallet address: ${wallet.address}`);

  // 2) Enviar create_incident on-chain (el SDK espera la confirmación)
  console.log("2️⃣  Enviando create_incident on-chain...");
  const tx = await callContract(wallet, createIncidentCall(incident));

  console.log(`✅ TX confirmada: id=${tx.transactionId}`);

  return {
    success: true,
    status: "success",
    txId: tx.transactionId,
    txHash: tx.hash,
    explorerLink: tx.explorerLink,
    walletAddress: wallet.address,
  };
}

module.exports = { createIncident };
