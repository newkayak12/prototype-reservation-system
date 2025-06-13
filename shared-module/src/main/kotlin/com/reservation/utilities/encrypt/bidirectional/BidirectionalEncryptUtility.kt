package com.reservation.utilities.encrypt.bidirectional

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

class BidirectionalEncryptUtility(
    key: String,
) {
    private val cipher: Ciphers

    companion object {
        private const val IV_LENGTH = 16
        private const val AES_ALGORITHM = "AES"
        private const val AES_KEY_BYTE_SIZE = 32
        private const val HEX = 16

        private const val CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding" // NOSONAR

        private fun hexToByteArray(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Hex key length must be even" }
            return ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(HEX).toByte()
            }
        }
    }

    init {

        val keyBytes = hexToByteArray(key)
        require(keyBytes.size == AES_KEY_BYTE_SIZE) { "AES-256 key must be exactly 32 bytes" }

        val ivBytes = keyBytes.sliceArray(0 until IV_LENGTH)
        val secretKey = SecretKeySpec(keyBytes, AES_ALGORITHM)
        val ivSpec = IvParameterSpec(ivBytes)

        val encode =
            Cipher.getInstance(CIPHER_ALGORITHM).apply {
                init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            }
        val decode =
            Cipher.getInstance(CIPHER_ALGORITHM).apply {
                init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            }

        cipher = Ciphers(encode, decode)
    }

    fun encrypt(plainText: String): String {
        return cipher.encode.doFinal(plainText.toByteArray(Charsets.UTF_8))
            .let { Base64.getEncoder().encodeToString(it) }
    }

    fun decrypt(encrypted: String): String {
        return Base64.getDecoder().decode(encrypted)
            .let { cipher.decode.doFinal(it) }
            .let { String(it, Charsets.UTF_8) }
    }

    private data class Ciphers(val encode: Cipher, val decode: Cipher)
}
