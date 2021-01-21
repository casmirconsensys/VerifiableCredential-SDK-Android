package com.microsoft.did.sdk.crypto.keys

import com.microsoft.did.sdk.crypto.models.webCryptoApi.KeyUsage

abstract class PrivateKey(
        kid: String,
        kty: KeyType,
        use: KeyUse,
        key_ops: List<KeyUsage> = emptyList(),
        alg: String
) : Key(kid, kty, use, key_ops, alg) {

    /**
     * Gets the corresponding public key
     * @returns The corresponding {@link PublicKey}
     */
    abstract fun getPublicKey(): PublicKey

    override fun minimumAlphabeticJwk(): String {
        return this.getPublicKey().minimumAlphabeticJwk()
    }
}