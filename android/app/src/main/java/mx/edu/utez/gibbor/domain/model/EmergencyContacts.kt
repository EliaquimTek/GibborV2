package mx.edu.utez.gibbor.domain.model

/**
 * Contactos de emergencia (prototipo): números de teléfono escritos por el usuario,
 * separados por coma, punto y coma o salto de línea.
 */
object EmergencyContacts {

    const val MAX_CONTACTS = 5
    private const val DEFAULT_COUNTRY_CODE = "52" // México

    /**
     * Convierte el texto capturado en números listos para SmsManager.
     * Quita espacios, guiones y paréntesis; un número de 10 dígitos sin lada se toma como +52.
     * Descarta entradas inválidas y repetidas.
     */
    fun parse(raw: String): List<String> =
        raw.split(',', ';', '\n')
            .mapNotNull { normalize(it) }
            .distinct()
            .take(MAX_CONTACTS)

    fun normalize(input: String): String? {
        val trimmed = input.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (trimmed.any { !it.isDigit() && it !in "+ -()." }) return null
        return when {
            hasPlus && digits.length in 8..15 -> "+$digits"
            !hasPlus && digits.length == 10 -> "+$DEFAULT_COUNTRY_CODE$digits"
            else -> null
        }
    }
}
