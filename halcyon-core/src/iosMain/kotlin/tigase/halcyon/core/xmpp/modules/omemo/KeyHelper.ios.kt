package tigase.halcyon.core.xmpp.modules.omemo

import kotlinx.cinterop.*
import libsignal.*
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual class KeyHelper() {
    
    @OptIn(ExperimentalForeignApi::class)
    actual companion object {
        private val signalContext: SignalContext = SignalContext.create();

        actual fun generateIdentityKeyPair(): IdentityKeyPair {
            val data = memScoped {
                val pointer = allocPointerTo<cnames.structs.ratchet_identity_key_pair>()
                var result = signal_protocol_key_helper_generate_identity_key_pair(pointer.ptr, signalContext.context);
                if (result < 0) {
                    TODO("Not yet implemented")
                }
                val buf = allocPointerTo<cnames.structs.signal_buffer>();
                result = ratchet_identity_key_pair_serialize(buf.ptr, pointer.value);
                if (result < 0) {
                    TODO("Not yet implemented")
                }
                val keyPairData = buf.value!!.toByteArray();
                signal_buffer_free(buf.value);
                return@memScoped keyPairData;
            }
            return IdentityKeyPair(data);
        }

        actual fun generateRegistrationId(extendedRange: Boolean): Int {
            return memScoped {
                val regId = alloc<UIntVar>();
                signal_protocol_key_helper_generate_registration_id(regId.ptr, if (extendedRange) { 1 } else { 0 }, signalContext.context);
                return@memScoped regId.value.toInt();
            }
        }

        actual fun generatePreKeys(start: Int, count: Int): List<PreKeyRecord> {
            return memScoped {
                val head = allocPointerTo<cnames.structs.signal_protocol_key_helper_pre_key_list_node>();
                if (signal_protocol_key_helper_generate_pre_keys(head.ptr, start.toUInt(), count.toUInt(), signalContext.context) < 0) {
                    TODO("Not yet implemented");
                }
                var keys = mutableListOf<PreKeyRecord>()
                while (head.value != null) {
                    val preKeyPointer = signal_protocol_key_helper_key_list_element(head.value);
                    val buf = allocPointerTo<cnames.structs.signal_buffer>();
                    session_pre_key_serialize(buf.ptr, preKeyPointer);
                    keys.add(PreKeyRecord(data = buf.value!!.toByteArray()));
                    signal_buffer_free(buf.value);
                    head.value = signal_protocol_key_helper_key_list_next(head.value);
                }
                return@memScoped keys;
            }
        }

        actual fun generateSignedPreKey(identityKeyPair: IdentityKeyPair, signedPreKeyId: Int): SignedPreKeyRecord {
            return identityKeyPair.pointerKeyPair { keyPairPointer ->
                val data = memScoped {
                    val pointer = allocPointerTo<cnames.structs.session_signed_pre_key>();
                    if (signal_protocol_key_helper_generate_signed_pre_key(pointer.ptr, keyPairPointer, signedPreKeyId.toUInt(), NSDate().timeIntervalSince1970.toULong() * 1000u, signalContext.context) < 0) {
                        TODO("Not yet implemented")
                    }
//            val id = session_signed_pre_key_get_id(pointer.value);
//            val signature = session_signed_pre_key_get_signature(pointer.value)!!.readBytes(
//                session_signed_pre_key_get_signature_len(pointer.value).toInt());


//            session_signed_pre_key_get_key_pair(pointer.value);
                    //SignedPreKeyRecord()
                    val buf = allocPointerTo<cnames.structs.signal_buffer>();
                    if (session_signed_pre_key_serialize(buf.ptr, pointer.value) < 0) {
                        TODO("Not yet implemented")
                    }
                    val data = buf.value!!.toByteArray();
                    signal_buffer_free(buf.value);
                    return@memScoped data;
                }
                return@pointerKeyPair SignedPreKeyRecord(data);
            }
        }
    }

}
