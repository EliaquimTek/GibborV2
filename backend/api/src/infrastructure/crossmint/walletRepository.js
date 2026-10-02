const { crossmintRequest } = require("./crossmintClient");

function walletLocatorFor(email) {
  return encodeURIComponent(`email:${email}:stellar:smart`);
}

/**
 * Consulta el estado de una transacción por REST (usado por el polling de la app).
 */
async function getTransaction(email, txId) {
  const txIdEncoded = encodeURIComponent(txId);
  return crossmintRequest("GET", `/wallets/${walletLocatorFor(email)}/transactions/${txIdEncoded}`);
}

module.exports = { getTransaction };
