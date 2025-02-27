package com.microsoft.did.sdk.identifier

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.microsoft.did.sdk.crypto.CryptoOperations
import com.microsoft.did.sdk.crypto.PrivateKeyFactoryAlgorithm
import com.microsoft.did.sdk.crypto.PublicKeyFactoryAlgorithm
import com.microsoft.did.sdk.crypto.keyStore.EncryptedKeyStore
import com.microsoft.did.sdk.util.Constants
import com.microsoft.did.sdk.util.defaultTestSerializer
import com.nimbusds.jose.jwk.OctetSequenceKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.UUID

class IdentifierCreatorTest {

    private val sideTreeHelper: SideTreeHelper = SideTreeHelper()
    private val payloadProcessor: SidetreePayloadProcessor = SidetreePayloadProcessor(sideTreeHelper, defaultTestSerializer)
    private val keyStore: EncryptedKeyStore = mockk(relaxed = true)

    private val identifierCreator = IdentifierCreator(payloadProcessor, sideTreeHelper, defaultTestSerializer, keyStore)

    private val firstKeyId = "6506aa67-1a51-42a0-a187-5442b3d1cd25"
    private val secondKeyId = "b83cca1e-4b3c-4f42-9739-463ae663f8db"
    private val thirdKeyId = "7bfb07f3-ac78-4907-a32c-f518fce9c403"
    private val personaName = "personaName"

    @Before
    fun init() {
        mockKeyGen()
        mockRandom()
    }

    @Test
    fun `identifier gets created properly`() {
        val actualIdentifier = identifierCreator.create(personaName)
        val expectedDid =
            "did:ion:EiD4wQ0yXIT9apnLPuyqc4W4j7Qg2ZS2fCEOH8P6c-ZDXw:eyJkZWx0YSI6eyJwYXRjaGVzIjpbeyJhY3Rpb24iOiJyZXBsYWNlIiwiZG9jdW1lbnQiOnsicHVibGljS2V5cyI6W3siaWQiOiI2NTA2YWE2NzFhNTE0MmEwYTE4NzU0NDJiM2QxY2QyNSIsInB1YmxpY0tleUp3ayI6eyJjcnYiOiJzZWNwMjU2azEiLCJraWQiOiI2NTA2YWE2NzFhNTE0MmEwYTE4NzU0NDJiM2QxY2QyNSIsImt0eSI6IkVDIiwieCI6ImRNZ3Fsck5TTTVjcG9TNFdYcldFbUhkSDJicEk2TTRzOU1EYVRrU0xWRVEiLCJ5IjoiX3ZTTTBoZTVsNmx6cER2OHBiMExtZUZBZlNncmR6enhWNzlpUklNTlN3byJ9LCJwdXJwb3NlcyI6WyJhdXRoZW50aWNhdGlvbiJdLCJ0eXBlIjoiRWNkc2FTZWNwMjU2azFWZXJpZmljYXRpb25LZXkyMDE5In1dfX1dLCJ1cGRhdGVDb21taXRtZW50IjoiRWlCVTUxeHJ0T1F2UmpQbEFnX3FSeXZFM3NyOXAyLVdKUTVTdGZYUXpuM01LQSJ9LCJzdWZmaXhEYXRhIjp7ImRlbHRhSGFzaCI6IkVpRDFfbk5VRllCME5wUW9vdlVCY0tGdWF6bW8zekZSRWxSeUVnS3A0eGZ1WGciLCJyZWNvdmVyeUNvbW1pdG1lbnQiOiJFaUJBYzZYckEyT3RGeVVYZUJ5Nmx1THFmeFo2ZGU4YV9jQnVLalBaZDZzMDVBIn19"
        assertThat(actualIdentifier.id).isEqualTo(expectedDid)
        assertThat(actualIdentifier.signatureKeyReference).isEqualTo(firstKeyId.replace("-", ""))
        assertThat(actualIdentifier.recoveryKeyReference).isEqualTo(secondKeyId.replace("-", ""))
        assertThat(actualIdentifier.updateKeyReference).isEqualTo(thirdKeyId.replace("-", ""))
        assertThat(actualIdentifier.name).isEqualTo(personaName)
    }

    private fun mockRandom() {
        mockkStatic(UUID::class)
        every { UUID.randomUUID() } returnsMany listOf(
            UUID.fromString(firstKeyId),
            UUID.fromString(secondKeyId),
            UUID.fromString(thirdKeyId),
            UUID.fromString(firstKeyId),
            UUID.fromString(secondKeyId),
            UUID.fromString(thirdKeyId)
        )
    }

    private fun mockKeyGen() {
        val keyPair1 = createKeyPair(
            "9764925054458648487875837691249734695986519782169248696406848767427475897986",
            "52821953784837859148297498991218118885836932354090668569542922319384208430148",
            "115319546132531470209270578804017583676805014907014973682639068158056817576714"
        )
        val keyPair2 = createKeyPair(
            "3459283827820697300966796890426889646838429304941694132637936337811118459952",
            "28252766365298059206925795131163559876531931493926681561665237302649218940973",
            "73715877015774520407277942328816129562063882432364992014294480019670305839425"
        )
        val keyPair3 = createKeyPair(
            "44014186799749441520388445423728502136656207534147308775871902455726178425047",
            "7451346941525076055613005204630396702007225960576988546203477435543751675272",
            "49884374232160137284684426104863332839560490360148060985107097260912360202460"
        )

        val keyGenMock = mockk<KeyPairGenerator>(relaxed = true)
        mockkStatic(KeyPairGenerator::class)
        every { KeyPairGenerator.getInstance(any()) } returns keyGenMock
        every { keyGenMock.genKeyPair() } returnsMany listOf(keyPair1, keyPair2, keyPair3, keyPair1, keyPair2, keyPair3)
    }

    private fun createKeyPair(s: String, x: String, y: String): KeyPair {
        val privateKey = CryptoOperations.generateKey<ECPrivateKey>(PrivateKeyFactoryAlgorithm.Secp256k1(BigInteger(s)))
        val publicKey = CryptoOperations.generateKey<ECPublicKey>(PublicKeyFactoryAlgorithm.Secp256k1(BigInteger(x), BigInteger(y)))
        return KeyPair(publicKey, privateKey)
    }

    @Test
    fun `pairwise Identifier gets created properly`() {
        val masterIdentifier = identifierCreator.create(personaName)
        val masterSeed = ByteArray(16, { it.toByte() })
        every { keyStore.getKey(Constants.MAIN_IDENTIFIER_REFERENCE) } returns OctetSequenceKey.Builder(masterSeed).build()
        val actualIdentifier = identifierCreator.createPairwiseId(masterIdentifier, "randomDid")
        val expectedDid = "did:ion:EiDm_INb-7RpbbbiftnuCXt033w6UCLiL7aUEZRSEtaydA:eyJkZWx0YSI6eyJwYXRjaGVzIjpbeyJhY3Rpb24iOiJyZXBsYWNlIiwiZG9jdW1lbnQiOnsicHVibGljS2V5cyI6W3siaWQiOiI2NTA2YWE2NzFhNTE0MmEwYTE4NzU0NDJiM2QxY2QyNSIsInB1YmxpY0tleUp3ayI6eyJjcnYiOiJzZWNwMjU2azEiLCJraWQiOiI2NTA2YWE2NzFhNTE0MmEwYTE4NzU0NDJiM2QxY2QyNSIsImt0eSI6IkVDIiwieCI6IkJCdkxxSkNyeWJoLUJhV1VOMDhLdG9ibUl4ME5SZUNCQTVDWDhKTzI0ZXciLCJ5IjoiaUJVM1A2TGt4VTJKb0Y3MnpFTEprUjdEZmxJLWNtN1F1aHBMZEZCTXJCYyJ9LCJwdXJwb3NlcyI6WyJhdXRoZW50aWNhdGlvbiJdLCJ0eXBlIjoiRWNkc2FTZWNwMjU2azFWZXJpZmljYXRpb25LZXkyMDE5In1dfX1dLCJ1cGRhdGVDb21taXRtZW50IjoiRWlDYllJcEJTaVU5dFVlOS1GdWd3Ti1MWmxPQS03dGlOWThHWWJUejFXY3JQdyJ9LCJzdWZmaXhEYXRhIjp7ImRlbHRhSGFzaCI6IkVpQW96Ty1JcGQ5SFFESjNOaUtVaF92UnVoc3JDX0ctbUR3QjExMjlQN1N6Z2ciLCJyZWNvdmVyeUNvbW1pdG1lbnQiOiJFaUJDYy1HQmt0NXo0Y1FXQUhHc1RFM3FocktxZG1nM0NyWDY1cEhWNVVBeDFBIn19"
        val expectedPairwiseName = "q9eFTKE8MG8LO_YzDIgmGw"
        assertThat(actualIdentifier.id).isEqualTo(expectedDid)
        assertThat(actualIdentifier.signatureKeyReference).isEqualTo(firstKeyId.replace("-", ""))
        assertThat(actualIdentifier.recoveryKeyReference).isEqualTo(secondKeyId.replace("-", ""))
        assertThat(actualIdentifier.updateKeyReference).isEqualTo(thirdKeyId.replace("-", ""))
        assertThat(actualIdentifier.name).isEqualTo(expectedPairwiseName)
    }
}