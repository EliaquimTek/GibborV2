package mx.edu.utez.gibbor.domain.model

data class BackendResult(
    val success: Boolean,
    val status: String,
    val txId: String,
    val txHash: String,
    val explorerLink: String,
    val error: String,
    val rawJson: String
)
