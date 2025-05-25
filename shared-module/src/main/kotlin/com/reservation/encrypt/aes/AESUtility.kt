package com.reservation.encrypt.aes

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

class AESUtility(
    key: String,
) {
    private val cipher: Ciphers

    companion object {
        private const val START_IV_INDEX = 0
        private const val END_IV_INDEX = 16
    }

    init {
        val algorithm = "AES"
        val cipherAlgorithm = "AES/CBC/PKCS5Padding"
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), algorithm)
        val ivParameterSpec =
            IvParameterSpec(key.substring(START_IV_INDEX, END_IV_INDEX).toByteArray())
        val encode =
            Cipher.getInstance(cipherAlgorithm).apply {
                init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec)
            }
        val decode =
            Cipher.getInstance(cipherAlgorithm).apply {
                init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
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
