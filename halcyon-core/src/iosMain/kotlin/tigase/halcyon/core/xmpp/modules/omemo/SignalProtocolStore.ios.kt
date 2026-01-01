@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("UnusedVariable", "UNUSED_VARIABLE", "UnusedParameter", "UNUSED_PARAMETER", "unused")

package tigase.halcyon.core.xmpp.modules.omemo

import cnames.structs.signal_buffer
import cnames.structs.signal_int_list
import kotlinx.cinterop.*
import libsignal.*
import platform.Foundation.NSRecursiveLock
import platform.posix.size_t
import platform.posix.uint32_t
import platform.posix.uint8_t
import platform.posix.uint8_tVar

class SignalContext(val context: CPointer<cnames.structs.signal_context>) {

    companion object {
        fun create(): SignalContext {
            val lock = NSRecursiveLock();
            val context = memScoped {
                val ctx = allocPointerTo<cnames.structs.signal_context>();
                val self = StableRef.create(lock).asCPointer();
                if (signal_context_create(ctx.ptr, self) != 0) {
                    TODO("Critical Error - handle it somehow?")
                }

                signal_context_set_crypto_provider(ctx.value, signalCryptoProvider.value.readValue());
                signal_context_set_locking_functions(ctx.value, staticCFunction { it ->
//                    try {
//                        throw RuntimeException();
//                    } catch (ex: Exception) {
//                        println("locking session context")
//                        ex.printStackTrace();
//                    }
                    it!!.asStableRef<NSRecursiveLock>().get().lock();
                    //lock.lock();
                }, staticCFunction { it ->
//                    try {
//                        throw RuntimeException();
//                    } catch (ex: Exception) {
//                        println("unlocking session context")
//                        ex.printStackTrace();
//                    }
                    it!!.asStableRef<NSRecursiveLock>().get().unlock();
                    //lock.unlock();
                })
                signal_context_set_log_function(ctx.value, staticCFunction { level, message, _, _ ->
                    print("SignalProtocol: " + level + " : " + message!!.toKStringFromUtf8())
                })
                return@memScoped ctx.value!!;
            }
            return SignalContext(context);
        }
    }

    fun release() {
        signal_context_destroy(context);
    }

    fun initializeContext(protocolStore: SignalProtocolStore): CPointer<cnames.structs.signal_protocol_store_context> {
        // FIXME: we need to call that in a proper location (keep it alive for OMEMOStore lifetime!!)
        val store = StableRef.create(protocolStore).asCPointer();

        return memScoped {
            val pointer = allocPointerTo<cnames.structs.signal_protocol_store_context>();
            signal_protocol_store_context_create(pointer.ptr, protocolStore.signalContext.context);

            val sessionStoreCallbacks = alloc<signal_protocol_session_store>();
            sessionStoreCallbacks.load_session_func = staticCFunction(::load_session_func)
            sessionStoreCallbacks.get_sub_device_sessions_func = staticCFunction(::get_sub_device_sessions_func);
            sessionStoreCallbacks.store_session_func = staticCFunction(::store_session_func);
            sessionStoreCallbacks.contains_session_func = staticCFunction(::contains_session_func);
            sessionStoreCallbacks.delete_session_func = staticCFunction(::delete_session_func)
            sessionStoreCallbacks.delete_all_sessions_func = staticCFunction(::delete_all_sessions_func)
            sessionStoreCallbacks.destroy_func = staticCFunction(::destroy_func);
            sessionStoreCallbacks.user_data = store;

            signal_protocol_store_context_set_session_store(pointer.value, sessionStoreCallbacks.ptr)

            val preKeyStoreCallBacks = alloc<signal_protocol_pre_key_store>();
            preKeyStoreCallBacks.load_pre_key = staticCFunction(::load_pre_key_func);
            preKeyStoreCallBacks.store_pre_key = staticCFunction(::store_pre_key_func);
            preKeyStoreCallBacks.contains_pre_key = staticCFunction(::contains_pre_key_func);
            preKeyStoreCallBacks.remove_pre_key = staticCFunction(::remove_pre_key_func);
            preKeyStoreCallBacks.destroy_func = staticCFunction(::destroy_func)
            preKeyStoreCallBacks.user_data = store;

            signal_protocol_store_context_set_pre_key_store(pointer.value, preKeyStoreCallBacks.ptr);

            val signedPreKeyStoreCallBacks = alloc<signal_protocol_signed_pre_key_store>();
            signedPreKeyStoreCallBacks.load_signed_pre_key = staticCFunction(::load_signed_pre_key_func);
            signedPreKeyStoreCallBacks.store_signed_pre_key = staticCFunction(::store_signed_pre_key_func);
            signedPreKeyStoreCallBacks.contains_signed_pre_key = staticCFunction(::contains_signed_pre_key_func);
            signedPreKeyStoreCallBacks.remove_signed_pre_key = staticCFunction(::remove_signed_pre_key_func)
            signedPreKeyStoreCallBacks.destroy_func = staticCFunction(::destroy_func)
            signedPreKeyStoreCallBacks.user_data = store;

            signal_protocol_store_context_set_signed_pre_key_store(pointer.value, signedPreKeyStoreCallBacks.ptr);

            val identityKeyStoreCallbacks = alloc<signal_protocol_identity_key_store>()
            identityKeyStoreCallbacks.get_identity_key_pair = staticCFunction(::get_identity_key_pair)
            identityKeyStoreCallbacks.get_local_registration_id = staticCFunction(::get_local_registration_id)
            identityKeyStoreCallbacks.save_identity = staticCFunction(::save_identity);
            identityKeyStoreCallbacks.is_trusted_identity = staticCFunction(::is_trusted_identity);
            identityKeyStoreCallbacks.destroy_func = staticCFunction(::destroy_func)
            identityKeyStoreCallbacks.user_data = store;

            signal_protocol_store_context_set_identity_key_store(pointer.value, identityKeyStoreCallbacks.ptr);

            return@memScoped pointer.value!!;
        }
    }

}

actual interface SignalProtocolStore : IdentityKeyStore, PreKeyStore, SessionStore, SignedPreKeyStore {

    val signalContext: SignalContext;
    var signalStoreContext: CPointer<cnames.structs.signal_protocol_store_context>?;

}

private fun load_session_func(
    record: CPointer<CPointerVar<signal_buffer>>?,
    userRecord: CPointer<CPointerVar<signal_buffer>>?,
    address: CPointer<signal_protocol_address>?,
    userData: COpaquePointer?
): Int {
    val store = userData!!.asStableRef<SignalProtocolStore>().get();
    val addr = SignalProtocolAddress.fromPointer(address!!);
    if (store.containsSession(addr)) {
        val sessionRecord = store.loadSession(addr);
        record!!.pointed.value = sessionRecord.data.toSignalBuffer() //signal_buffer_create(sessionRecord.data.toUx).toCValues(), sessionRecord.data.size.toULong());
        return 1;
    } else {
        return 0;
    }
}

private fun get_sub_device_sessions_func(
    sessions: CPointer<CPointerVar<signal_int_list>>?,
    name: CPointer<ByteVar>?,
    nameLen: size_t,
    userData: COpaquePointer?
): Int {
    val nameStr = name!!.readBytes(nameLen.toInt()).toKString();
    val devices = userData!!.asStableRef<SignalProtocolStore>().get().getSubDeviceSessions(nameStr);
    val list = signal_int_list_alloc();
    for (device in devices) {
        signal_int_list_push_back(list, device);
    }
    sessions!!.pointed.value = list;
    return devices.size;
}

private fun store_session_func(
    address: CPointer<signal_protocol_address>?,
    record: CPointer<uint8_tVar>?,
    recordLen: size_t,
    userRecord: CPointer<uint8_tVar>?,
    userRecordLen: size_t,
    userData: COpaquePointer?
): Int {
    val addr = SignalProtocolAddress.fromPointer(address!!);
    val data = record!!.readBytes(recordLen.toInt());
    userData!!.asStableRef<SignalProtocolStore>().get().storeSession(addr, SessionRecord(data));
    return 1;
}

private fun contains_session_func(
    address: CPointer<signal_protocol_address>?,
    userData: COpaquePointer?
): Int {
    val addr = SignalProtocolAddress.fromPointer(address!!);
    val result = userData!!.asStableRef<SignalProtocolStore>().get().containsSession(addr);
    return if (result) 1 else 0;
}

private fun delete_session_func(
    address: CPointer<signal_protocol_address>?,
    userData: COpaquePointer?
): Int {
    val addr = SignalProtocolAddress.fromPointer(address!!);
    userData!!.asStableRef<SignalProtocolStore>().get().deleteSession(addr);
    return 1;
}

private fun delete_all_sessions_func(
    name: CPointer<ByteVar>?,
    nameLen: size_t,
    userData: COpaquePointer?
): Int {
    val nameStr = name!!.readBytes(nameLen.toInt()).toKString();
    userData!!.asStableRef<SignalProtocolStore>().get().deleteAllSessions(nameStr);
    return 1;
}

private fun destroy_func(
    userData: COpaquePointer?
) {
    // nothing to do..
}

private fun load_pre_key_func(record:  CPointer<CPointerVar<signal_buffer>>?, preKeyId: UInt, userData: COpaquePointer?): Int {
    val preKey = userData!!.asStableRef<SignalProtocolStore>().get().loadPreKey(preKeyId.toInt());
    record!!.pointed.value = preKey.serialize().toSignalBuffer();
    return SG_SUCCESS;
}

private fun store_pre_key_func(
    preKeyId: UInt, record: CPointer<uint8_tVar>?,
    recordLen: size_t, userData: COpaquePointer?
): Int {
    val data = record!!.readBytes(recordLen.toInt());
    userData!!.asStableRef<SignalProtocolStore>().get().storePreKey(preKeyId.toInt(), PreKeyRecord(data));
    return 1;
}

private fun contains_pre_key_func(
    preKeyId: UInt, userData: COpaquePointer?
): Int {
    val result = userData!!.asStableRef<SignalProtocolStore>().get().containsPreKey(preKeyId.toInt());
    return if (result) 1 else 0;
}

private fun remove_pre_key_func(preKeyId: UInt, userData: COpaquePointer?): Int {
    val result = userData!!.asStableRef<SignalProtocolStore>().get().removePreKey(preKeyId.toInt());
    return 1;
}

private fun load_signed_pre_key_func(record:  CPointer<CPointerVar<signal_buffer>>?, signedPreKeyId: UInt, userData: COpaquePointer?): Int {
    val preKey = userData!!.asStableRef<SignalProtocolStore>().get().loadSignedPreKey(signedPreKeyId.toInt());
    record!!.pointed.value = preKey.data.toSignalBuffer() //signal_buffer_create(preKey.data.toUByteArray().toCValues(), preKey.data.size.toULong());
    return SG_SUCCESS;
}

private fun store_signed_pre_key_func(
    signedPreKeyId: UInt, record: CPointer<uint8_tVar>?,
    recordLen: size_t, userData: COpaquePointer?
): Int {
    val data = record!!.readBytes(recordLen.toInt());
    userData!!.asStableRef<SignalProtocolStore>().get().storePreKey(signedPreKeyId.toInt(), PreKeyRecord(data));
    return 1;
}

private fun contains_signed_pre_key_func(
    signedPreKeyId: UInt, userData: COpaquePointer?
): Int {
    val result = userData!!.asStableRef<SignalProtocolStore>().get().containsPreKey(signedPreKeyId.toInt());
    return if (result) 1 else 0;
}

private fun remove_signed_pre_key_func(signedPreKeyId: UInt, userData: COpaquePointer?): Int {
    val result = userData!!.asStableRef<SignalProtocolStore>().get().removePreKey(signedPreKeyId.toInt());
    return 1;
}

private fun get_identity_key_pair(publicData: CPointer<CPointerVar<signal_buffer>>?, privateData: CPointer<CPointerVar<signal_buffer>>?, userData: COpaquePointer?): Int {
    val identityKeyPair = userData!!.asStableRef<SignalProtocolStore>().get().getIdentityKeyPair();
    publicData!!.pointed.value = identityKeyPair.getPublicKey().serialize().toSignalBuffer();
    privateData!!.pointed.value = identityKeyPair.getPrivateKey().privateKey.toSignalBuffer()

    return SG_SUCCESS;
}

private fun get_local_registration_id(userData: COpaquePointer?, registrationId: CPointer<UIntVarOf<uint32_t>>?): Int {
    registrationId!!.pointed.value = userData!!.asStableRef<SignalProtocolStore>().get().getLocalRegistrationId().toUInt();
    return 1;
}

private fun save_identity(address: CPointer<signal_protocol_address>?, keyData: CPointer<UByteVarOf<uint8_t>>?, keyLen: ULong, userData: COpaquePointer?): Int {
    val addr = SignalProtocolAddress.fromPointer(address!!);
    val identityKey = IdentityKey(ECPublicKeyImpl(keyData!!.readBytes(keyLen.toInt())));
    userData!!.asStableRef<SignalProtocolStore>().get().saveIdentity(addr, identityKey);
    return 1;
}

private fun is_trusted_identity(address: CPointer<signal_protocol_address>?,  keyData: CPointer<UByteVarOf<uint8_t>>?, keyLen: ULong, userData: COpaquePointer?): Int {
    val addr = SignalProtocolAddress.fromPointer(address!!);
    val identityKey = IdentityKey(ECPublicKeyImpl(keyData!!.readBytes(keyLen.toInt())));
    val result = userData!!.asStableRef<SignalProtocolStore>().get().isTrustedIdentity(addr, identityKey, IdentityKeyStoreDirection.SENDING);
    return if (result) 1 else 0;
}

fun ByteArray.toSignalBuffer(): CPointer<signal_buffer>? {
    return signal_buffer_create(this.toUByteArray().toCValues(), this.size.toULong());
}

fun CPointer<signal_buffer>.toByteArray(): ByteArray {
    val len = signal_buffer_len(this).toInt();
    if (len < 0) {
        throw IllegalArgumentException("Invalid signal buffer read length: $len")
    }
    return signal_buffer_data(this)!!.readBytes(len);
}