const { crossmintRequest } = require("./crossmintClient");

function walletLocatorFor(email) {
  return encodeURIComponent(`email:${email}:stellar:smart`);
}

/**
 * Crea la wallet Stellar del usuario. 409 = wallet ya existe, eso está bien.
 */
async function ensureStellarWallet(email) {
  try {
    return await crossmintRequest("POST", "/wallets", {
      chainType: "stellar",
      type: "smart",
      owner: `email:${email}`,
    });
  } catch (err) {
    if (err.status === 409) {
      console.log("   Wallet ya existe (409) — OK");
      return err.body;
    }
    throw err;
  }
}

async function createTransaction(email, txBody) {
  return crossmintRequest("POST", `/wallets/${walletLocatorFor(email)}/transactions`, txBody);
}

async function getTransaction(email, txId) {
  const txIdEncoded = encodeURIComponent(txId);
  return crossmintRequest("GET", `/wallets/${walletLocatorFor(email)}/transactions/${txIdEncoded}`);
}

module.exports = { ensureStellarWallet, createTransaction, getTransaction };
