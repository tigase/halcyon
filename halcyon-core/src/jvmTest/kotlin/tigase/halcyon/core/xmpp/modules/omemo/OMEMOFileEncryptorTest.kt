package tigase.halcyon.core.xmpp.modules.omemo

import korlibs.crypto.encoding.unhex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class OMEMOFileEncryptorTest {

    /**
     * Test vectors taken from [The Galois/Counter Mode of Operation (GCM)](https://luca-giuzzi.unibs.it/corsi/Support/papers-cryptography/gcm-spec.pdf)
     * by David A. McGrew and John Viega
     *
     * Test case 14
     */
    @Test
    fun encryptionTestZero() {
        val key = "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000".unhex
        assertEquals(12 + 32, key.size)

        val m = "00000000000000000000000000000000".unhex
        val c = ByteArrayOutputStream().also {
            OMEMOFileEncryptor.encrypt(ByteArrayInputStream(m), key, it)

        }.toByteArray()

        assertContentEquals("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919".unhex, c)
    }


    /**
     * Test vectors taken from [The Galois/Counter Mode of Operation (GCM)](https://luca-giuzzi.unibs.it/corsi/Support/papers-cryptography/gcm-spec.pdf)
     * by David A. McGrew and John Viega
     *
     * Test case 14
     */
    @Test
    fun decryptionTestZero() {
        val key = "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000".unhex
        assertEquals(12 + 32, key.size)

        val c = "cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919".unhex
        val m = ByteArrayOutputStream().also {
            OMEMOFileEncryptor.decrypt(ByteArrayInputStream(c), key, it)

        }.toByteArray()

        assertContentEquals("00000000000000000000000000000000".unhex, m)
    }

    /**
     * Test vectors taken from [The Galois/Counter Mode of Operation (GCM)](https://luca-giuzzi.unibs.it/corsi/Support/papers-cryptography/gcm-spec.pdf)
     * by David A. McGrew and John Viega
     *
     * Test case 15
     */
    @Test
    fun encryptionTestCaFeBaBe() {
        val key = "cafebabefacedbaddecaf888feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308".unhex
        assertEquals(12 + 32, key.size)

        assertContentEquals("cafebabefacedbaddecaf888".unhex, OMEMOFileEncryptor.getIv(key), "Invalid IV")
        assertContentEquals(
            "feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308".unhex,
            OMEMOFileEncryptor.getKey(key),
            "Invalid KEY"
        )


        val m =
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255".unhex
        val c = ByteArrayOutputStream().also {
            OMEMOFileEncryptor.encrypt(ByteArrayInputStream(m), key, it)

        }.toByteArray()

        assertContentEquals(
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662898015adb094dac5d93471bdec1a502270e3cc6c".unhex,
            c
        )
    }

    /**
     * Test vectors taken from [The Galois/Counter Mode of Operation (GCM)](https://luca-giuzzi.unibs.it/corsi/Support/papers-cryptography/gcm-spec.pdf)
     * by David A. McGrew and John Viega
     *
     * Test case 15
     */
    @Test
    fun decryptionTestCaFeBaBe() {
        val key = "cafebabefacedbaddecaf888feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308".unhex
        assertEquals(12 + 32, key.size)

        val c =
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662898015adb094dac5d93471bdec1a502270e3cc6c".unhex
        val m = ByteArrayOutputStream().also {
            OMEMOFileEncryptor.decrypt(ByteArrayInputStream(c), key, it)

        }.toByteArray()

        assertContentEquals(
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255".unhex,
            m
        )
    }


    @Test
    fun encryptAndDecryptShort() {
        val key = OMEMOFileEncryptor.generateIvAndKey()

        val plaintext = "a".toByteArray()
        val cipherOutputStream = ByteArrayOutputStream()

        OMEMOFileEncryptor.encrypt(ByteArrayInputStream(plaintext), key.unhex, cipherOutputStream)

        val ciphertext = cipherOutputStream.toByteArray()
        assertTrue(ciphertext.isNotEmpty(), "Ciphertext MUST not be empty!")

        val plaintextOutputStream = ByteArrayOutputStream()
        OMEMOFileEncryptor.decrypt(ByteArrayInputStream(ciphertext), key.unhex, plaintextOutputStream)


        val decryptedPlaintext = plaintextOutputStream.toByteArray()
        assertTrue(decryptedPlaintext.isNotEmpty(), "Decrypted plaintext MUST not be empty!")

        assertEquals(plaintext.size, decryptedPlaintext.size, "Decrypted data length is invalid.")
        assertContentEquals(plaintext, decryptedPlaintext)
    }

    @Test
    fun encryptAndDecryptLong() {
        val key = OMEMOFileEncryptor.generateIvAndKey()

        val plaintext = ByteArray(2041).also {
            Random.nextBytes(it)
        }
        val cipherOutputStream = ByteArrayOutputStream()

        OMEMOFileEncryptor.encrypt(ByteArrayInputStream(plaintext), key.unhex, cipherOutputStream)

        val ciphertext = cipherOutputStream.toByteArray()
        assertTrue(ciphertext.isNotEmpty(), "Ciphertext MUST not be empty!")

        val plaintextOutputStream = ByteArrayOutputStream()
        OMEMOFileEncryptor.decrypt(ByteArrayInputStream(ciphertext), key.unhex, plaintextOutputStream)


        val decryptedPlaintext = plaintextOutputStream.toByteArray()
        assertTrue(decryptedPlaintext.isNotEmpty(), "Decrypted plaintext MUST not be empty!")

        assertEquals(plaintext.size, decryptedPlaintext.size, "Decrypted data length is invalid.")
        assertContentEquals(plaintext, decryptedPlaintext)
    }
}