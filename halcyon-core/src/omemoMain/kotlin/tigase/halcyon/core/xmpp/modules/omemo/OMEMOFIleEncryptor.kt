package tigase.halcyon.core.xmpp.modules.omemo

expect abstract class InputStream {}
expect abstract class OutputStream {}

expect object OMEMOFileEncryptor {

    fun cipherOutputStream(keyAndIv: ByteArray, output: OutputStream): OutputStream;
    fun cipherInputStream(keyAndIv: ByteArray, input: InputStream): InputStream;
    
    fun encrypt(input: InputStream, keyAndIv: ByteArray, output: OutputStream);
    fun decrypt(input: InputStream, keyAndIv: ByteArray, output: OutputStream);
}