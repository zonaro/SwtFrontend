package br.com.redclaw.swt.module.api

/** Checksums and accepted byte orders used to identify a user-provided ROM. */
data class RomIdentity(
    val crc32: String,
    val sha1: String? = null,
    val acceptedByteOrders: Set<RomByteOrder> = setOf(RomByteOrder.NATIVE),
) {
    init {
        require(crc32.matches(CRC32_PATTERN)) { "CRC32 must contain exactly 8 hexadecimal characters" }
        require(sha1 == null || sha1.matches(SHA1_PATTERN)) {
            "SHA-1 must contain exactly 40 hexadecimal characters"
        }
        require(acceptedByteOrders.isNotEmpty()) { "At least one ROM byte order is required" }
    }

    enum class RomByteOrder {
        NATIVE,
        BYTE_SWAPPED,
        LITTLE_ENDIAN,
    }

    companion object {
        private val CRC32_PATTERN = Regex("[0-9a-fA-F]{8}")
        private val SHA1_PATTERN = Regex("[0-9a-fA-F]{40}")
    }
}
