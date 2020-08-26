package com.microsoft.did.sdk.credential.service.protectors

import com.microsoft.did.sdk.credential.models.VerifiableCredential
import com.microsoft.did.sdk.credential.models.VerifiableCredentialHolder
import com.microsoft.did.sdk.credential.service.IssuanceResponse
import com.microsoft.did.sdk.credential.service.PresentationResponse
import com.microsoft.did.sdk.credential.service.RequestedIdTokenMap
import com.microsoft.did.sdk.credential.service.RequestedSelfAttestedClaimMap
import com.microsoft.did.sdk.credential.service.RequestedVchMap
import com.microsoft.did.sdk.credential.service.RequestedVchPresentationSubmissionMap
import com.microsoft.did.sdk.credential.service.models.attestations.PresentationAttestation
import com.microsoft.did.sdk.credential.service.models.oidc.IssuanceResponseClaims
import com.microsoft.did.sdk.credential.service.models.oidc.PresentationResponseClaims
import com.microsoft.did.sdk.credential.service.models.presentationexchange.CredentialPresentationInputDescriptor
import com.microsoft.did.sdk.credential.service.models.presentationexchange.Schema
import com.microsoft.did.sdk.crypto.CryptoOperations
import com.microsoft.did.sdk.crypto.keyStore.KeyStore
import com.microsoft.did.sdk.crypto.keys.KeyContainer
import com.microsoft.did.sdk.crypto.keys.PublicKey
import com.microsoft.did.sdk.crypto.models.Sha
import com.microsoft.did.sdk.crypto.models.webCryptoApi.JsonWebKey
import com.microsoft.did.sdk.identifier.models.Identifier
import com.microsoft.did.sdk.util.serializer.Serializer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.test.assertEquals

class OidcResponseFormatterTest {

    // mocks for retrieving public key.
    private val mockedCryptoOperations: CryptoOperations = mockk()
    private val mockedKeyStore: KeyStore = mockk()
    private val mockedKeyContainer: KeyContainer<PublicKey> = mockk()
    private val mockedPublicKey: PublicKey = mockk()

    private val mockedTokenSigner: TokenSigner = mockk()
    private val slot = slot<String>()
    private val mockedVerifiablePresentationFormatter: VerifiablePresentationFormatter = mockk()
    private val mockedVc: VerifiableCredential = mockk()
    private val mockedVch: VerifiableCredentialHolder = mockk()
    private val mockedIdentifier: Identifier = mockk()
    private val serializer: Serializer = Serializer()

    private val issuanceResponseFormatter: IssuanceResponseFormatter
    private val presentationResponseFormatter: PresentationResponseFormatter

    private val signingKeyRef: String = "sigKeyRef1243523"
    private val expectedDid: String = "did:test:2354543"
    private val expectedValidityInterval: Int = 32958
    private val expectedContract = "http://testcontract.com"
    private val expectedResponseAudience: String = "audience2432"
    private val expectedPresentationAudience: String = "audience6237"
    private val expectedThumbprint: String = "thumbprint534233"
    private val expectedExpiry: Int = 42
    private val expectedJsonWebKey = JsonWebKey()
    private val expectedVerifiablePresentation = "expectedPresentation"
    private val expectedSelfAttestedField = "testField3423442"
    private val expectedIdTokenConfig = "testIdTokenConfig234"
    private val expectedCredentialType: String = "type235"

    private val mockedPresentationResponse: PresentationResponse = mockk()
    private val mockedNonce = "123456789876"
    private val mockedState = "mockedState"
    private val credentialSchema = Schema(listOf("https://schema.org/testcredential1", "https://schema.org/testcredential2"))
    private val credentialPresentationInputDescriptors = CredentialPresentationInputDescriptor("mocked_presentation_Input1", credentialSchema)
    private val requestedVchPresentationSubmissionMap = mapOf(credentialPresentationInputDescriptors to mockedVch) as RequestedVchPresentationSubmissionMap

    private val mockedIssuanceResponse: IssuanceResponse = mockk()
    private val expectedRawToken = "rawToken2343"
    private val requestedIdTokenMap =  mapOf(expectedIdTokenConfig to expectedRawToken) as RequestedIdTokenMap
    private val expectedSelfAttestedClaimValue = "value5234"
    private val requestedSelfAttestedClaimsMap = mapOf(expectedSelfAttestedField to expectedSelfAttestedClaimValue) as RequestedSelfAttestedClaimMap
    private val mockedPresentationAttestation: PresentationAttestation = mockk()
    private val mockedRequestedVchMap: RequestedVchMap = mutableMapOf(mockedPresentationAttestation to mockedVch)

    init {
        issuanceResponseFormatter = IssuanceResponseFormatter(
            mockedCryptoOperations,
            serializer,
            mockedVerifiablePresentationFormatter,
            mockedTokenSigner
        )
        presentationResponseFormatter = PresentationResponseFormatter(
            mockedCryptoOperations,
            serializer,
            mockedVerifiablePresentationFormatter,
            mockedTokenSigner
        )
        setUpGetPublicKey()
        setUpExpectedPresentations()
        every { mockedTokenSigner.signWithIdentifier(capture(slot), mockedIdentifier) } answers { slot.captured }
        every {
            mockedVerifiablePresentationFormatter.createPresentation(
                mockedVc,
                expectedValidityInterval,
                any(),
                mockedIdentifier
            )
        } returns expectedVerifiablePresentation
        mockPresentationResponse()
        mockIssuanceResponseWithNoAttestations()
        mockPresentationAttestation()
    }

    private fun setUpGetPublicKey() {
        every { mockedIdentifier.signatureKeyReference } returns signingKeyRef
        every { mockedIdentifier.id } returns expectedDid
        every { mockedCryptoOperations.keyStore } returns mockedKeyStore
        every { mockedKeyStore.getPublicKey(signingKeyRef) } returns mockedKeyContainer
        every { mockedKeyContainer.getKey() } returns mockedPublicKey
        every { mockedPublicKey.getThumbprint(mockedCryptoOperations, Sha.SHA256.algorithm) } returns expectedThumbprint
        every { mockedPublicKey.toJWK() } returns expectedJsonWebKey
    }

    private fun setUpExpectedPresentations() {
        every {
            mockedVerifiablePresentationFormatter.createPresentation(
                mockedVc,
                expectedValidityInterval,
                expectedPresentationAudience,
                mockedIdentifier
            )
        } returns expectedVerifiablePresentation
        every { mockedVch.verifiableCredential } returns mockedVc
    }

    @Test
    fun `format presentation response with no attestations`() {
        val actualFormattedToken = presentationResponseFormatter.formatResponse(
            mutableMapOf(),
            mockedPresentationResponse,
            expectedExpiry
        )
        val actualTokenContents = serializer.parse(PresentationResponseClaims.serializer(), actualFormattedToken)
        assertEquals(expectedPresentationAudience, actualTokenContents.aud)
        assertEquals(mockedState, actualTokenContents.state)
        assertEquals(mockedNonce, actualTokenContents.nonce)
        assertEquals(expectedDid, actualTokenContents.did)
        assertEquals(expectedThumbprint, actualTokenContents.sub)
        assertEquals(expectedJsonWebKey, actualTokenContents.publicKeyJwk)
        assertThat(actualTokenContents.attestations.idTokens.size).isEqualTo(0)
        assertThat(actualTokenContents.attestations.presentations.size).isEqualTo(0)
        assertThat(actualTokenContents.attestations.selfIssued.size).isEqualTo(0)
    }

    @Test
    fun `format issuance response with no attestations`() {
        every { mockedIssuanceResponse.getRequestedIdTokens() } returns mutableMapOf()
        every { mockedIssuanceResponse.getRequestedSelfAttestedClaims() } returns mutableMapOf()
        val actualFormattedToken = issuanceResponseFormatter.formatResponse(
            mutableMapOf(),
            mockedIssuanceResponse,
            expectedExpiry
        )
        val actualTokenContents = serializer.parse(IssuanceResponseClaims.serializer(), actualFormattedToken)
        assertEquals(expectedResponseAudience, actualTokenContents.aud)
        assertEquals(expectedContract, actualTokenContents.contract)
        assertEquals(expectedDid, actualTokenContents.did)
        assertEquals(expectedThumbprint, actualTokenContents.sub)
        assertEquals(expectedJsonWebKey, actualTokenContents.publicKeyJwk)
        assertThat(actualTokenContents.attestations.idTokens.size).isEqualTo(0)
        assertThat(actualTokenContents.attestations.presentations.size).isEqualTo(0)
        assertThat(actualTokenContents.attestations.selfIssued.size).isEqualTo(0)
    }

    @Test
    fun `format issuance response with id-token attestations`() {
        every { mockedIssuanceResponse.getRequestedIdTokens() } returns requestedIdTokenMap
        every { mockedIssuanceResponse.getRequestedSelfAttestedClaims() } returns mutableMapOf()
        val actualFormattedToken = issuanceResponseFormatter.formatResponse(
            mutableMapOf(),
            mockedIssuanceResponse,
            expectedExpiry
        )
        val actualTokenContents = serializer.parse(IssuanceResponseClaims.serializer(), actualFormattedToken)
        assertEquals(expectedResponseAudience, actualTokenContents.aud)
        assertEquals(expectedContract, actualTokenContents.contract)
        assertEquals(expectedDid, actualTokenContents.did)
        assertEquals(expectedThumbprint, actualTokenContents.sub)
        assertEquals(expectedJsonWebKey, actualTokenContents.publicKeyJwk)
        assertEquals(expectedRawToken, actualTokenContents.attestations.idTokens.entries.first().value)
        assertEquals(expectedIdTokenConfig, actualTokenContents.attestations.idTokens.entries.first().key)
        assertThat(actualTokenContents.attestations.selfIssued.size).isEqualTo(0)
        assertThat(actualTokenContents.attestations.presentations.size).isEqualTo(0)
    }

    @Test
    fun `format issuance response with self attested attestations`() {
        every { mockedIssuanceResponse.getRequestedSelfAttestedClaims() } returns requestedSelfAttestedClaimsMap
        every { mockedIssuanceResponse.getRequestedIdTokens() } returns mutableMapOf()
        val actualFormattedToken = issuanceResponseFormatter.formatResponse(
            mutableMapOf(),
            mockedIssuanceResponse,
            expectedExpiry
        )
        val actualTokenContents = serializer.parse(IssuanceResponseClaims.serializer(), actualFormattedToken)
        assertEquals(expectedResponseAudience, actualTokenContents.aud)
        assertEquals(expectedContract, actualTokenContents.contract)
        assertEquals(expectedDid, actualTokenContents.did)
        assertEquals(expectedThumbprint, actualTokenContents.sub)
        assertEquals(expectedJsonWebKey, actualTokenContents.publicKeyJwk)
        assertEquals(expectedSelfAttestedField, actualTokenContents.attestations.selfIssued.entries.first().key)
        assertEquals(expectedSelfAttestedClaimValue, actualTokenContents.attestations.selfIssued.entries.first().value)
        assertThat(actualTokenContents.attestations.idTokens.size).isEqualTo(0)
        assertThat(actualTokenContents.attestations.presentations.size).isEqualTo(0)
    }

    @Test
    fun `format issuance response with presentation attestations`() {
        every { mockedIssuanceResponse.getRequestedVchs() } returns mockedRequestedVchMap
        every { mockedIssuanceResponse.getRequestedIdTokens() } returns mutableMapOf()
        every { mockedIssuanceResponse.getRequestedSelfAttestedClaims() } returns mutableMapOf()
        val actualFormattedToken = issuanceResponseFormatter.formatResponse(
            mockedRequestedVchMap,
            mockedIssuanceResponse,
            expectedExpiry
        )
        val actualTokenContents = serializer.parse(IssuanceResponseClaims.serializer(), actualFormattedToken)
        assertEquals(expectedResponseAudience, actualTokenContents.aud)
        assertEquals(expectedContract, actualTokenContents.contract)
        assertEquals(expectedDid, actualTokenContents.did)
        assertEquals(expectedThumbprint, actualTokenContents.sub)
        assertEquals(expectedJsonWebKey, actualTokenContents.publicKeyJwk)
        assertEquals(mapOf(expectedCredentialType to expectedVerifiablePresentation), actualTokenContents.attestations.presentations)
        assertThat(actualTokenContents.attestations.idTokens.size).isEqualTo(0)
        assertThat(actualTokenContents.attestations.selfIssued.size).isEqualTo(0)
    }

    @Test
    fun `format issuance response with all attestations`() {
        val expectedRawToken = "rawToken2343"
        every { mockedIssuanceResponse.getRequestedIdTokens() } returns requestedIdTokenMap
        every { mockedIssuanceResponse.getRequestedSelfAttestedClaims() } returns requestedSelfAttestedClaimsMap
        every { mockedIssuanceResponse.getRequestedVchs() } returns mockedRequestedVchMap
        every { mockedIssuanceResponse.request.entityIdentifier } returns expectedResponseAudience
        val results = issuanceResponseFormatter.formatResponse(
            mockedRequestedVchMap,
            mockedIssuanceResponse,
            expectedExpiry
        )
        val actualTokenContents = serializer.parse(IssuanceResponseClaims.serializer(), results)
        assertEquals(expectedResponseAudience, actualTokenContents.aud)
        assertEquals(expectedContract, actualTokenContents.contract)
        assertEquals(expectedDid, actualTokenContents.did)
        assertEquals(expectedThumbprint, actualTokenContents.sub)
        assertEquals(expectedJsonWebKey, actualTokenContents.publicKeyJwk)
        assertEquals(mapOf(expectedCredentialType to expectedVerifiablePresentation), actualTokenContents.attestations.presentations)
        assertEquals(expectedSelfAttestedField, actualTokenContents.attestations.selfIssued.entries.first().key)
        assertEquals(expectedSelfAttestedClaimValue, actualTokenContents.attestations.selfIssued.entries.first().value)
        assertEquals(expectedRawToken, actualTokenContents.attestations.idTokens.entries.first().value)
        assertEquals(expectedIdTokenConfig, actualTokenContents.attestations.idTokens.entries.first().key)
    }

    private fun mockPresentationResponse() {
        every { mockedPresentationResponse.request.entityIdentifier } returns expectedDid
        every { mockedPresentationResponse.audience } returns expectedPresentationAudience
        every { mockedPresentationResponse.request.requestContent.nonce } returns mockedNonce
        every { mockedPresentationResponse.request.requestContent.state } returns mockedState
        every { mockedPresentationResponse.getRequestedVchClaims() } returns requestedVchPresentationSubmissionMap
        every { mockedPresentationResponse.responder } returns mockedIdentifier
    }

    private fun mockIssuanceResponseWithNoAttestations() {
        every { mockedIssuanceResponse.request.entityIdentifier } returns expectedDid
        every { mockedIssuanceResponse.audience } returns expectedResponseAudience
        every { mockedIssuanceResponse.request.contractUrl } returns expectedContract
        every { mockedIssuanceResponse.responder } returns mockedIdentifier
    }

    private fun mockPresentationAttestation() {
        every { mockedPresentationAttestation.credentialType } returns expectedCredentialType
        every { mockedPresentationAttestation.validityInterval } returns expectedValidityInterval
    }
}