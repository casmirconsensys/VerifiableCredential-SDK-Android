package com.microsoft.portableIdentity.sdk.auth.protocolManagers

import com.microsoft.did.sdk.credentials.Credential
import com.microsoft.portableIdentity.sdk.auth.credentialRequests.CredentialRequests
import com.microsoft.portableIdentity.sdk.auth.models.ResponseContent
import com.microsoft.portableIdentity.sdk.auth.models.oidc.OIDCRequestContent
import com.microsoft.portableIdentity.sdk.auth.models.oidc.OIDCResponseContent
import com.microsoft.portableIdentity.sdk.auth.protectors.Protector
import com.microsoft.portableIdentity.sdk.auth.protectors.Signer
import com.microsoft.portableIdentity.sdk.auth.validators.Validator
import com.microsoft.portableIdentity.sdk.crypto.protocols.jose.jws.JwsFormat
import com.microsoft.portableIdentity.sdk.crypto.protocols.jose.jws.JwsToken
import com.microsoft.portableIdentity.sdk.utilities.BaseLogger
import com.microsoft.portableIdentity.sdk.utilities.Serializer
import io.ktor.http.Url
import io.ktor.util.toMap
import java.lang.Exception
import java.util.*

class OIDCProtocolManager(rawRequest: String): ProtocolManager {

    override val responseUri: String

    private val requestParameters: Map<String, List<String>>

    private val requestToken: JwsToken?

    private val requestContent: OIDCRequestContent?

    init {
        val openIdUrl = Url(rawRequest)
        requestParameters = openIdUrl.parameters.toMap()

        val serializedToken: String? = requestParameters["request"]?.first()

        if (serializedToken is String) {
            try {
                requestToken = JwsToken.deserialize(serializedToken, BaseLogger)
                requestContent = Serializer.parse(OIDCRequestContent.serializer(), requestToken.content())
                responseUri = requestContent.responseUri

            } catch (exception: Exception) {
                TODO("check to see if request can be a different type of token.")
            }
        } else {
            requestToken = null
            requestContent = null
            val responseUri = requestParameters["redirect_uri"]?.first()
            if (responseUri is String) {
                this.responseUri = responseUri
            } else {
                throw Exception("No Response Uri in the Request.")
            }
        }
    }

    override fun getCredentialRequests(): CredentialRequests {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * TODO(check to see if all parameters are there?
     */
    override suspend fun isRequestValid(validator: Validator): Boolean {

        if (requestToken == null || requestContent == null) {
            // TODO(should we be throwing here or just returning true?)
            throw Exception("nothing to validate")
        }

        // 1. check the signature on the jwsToken
        if (!validator.verify(requestToken, requestContent.requester)) {
            return false
        }

        // 2. Check the expiration on the token.
        val currentTime = Date().time
        // check exp
        val milliseconds = 1000
        // set a leeway of 5 minutes.
        val expirationCheckTimeOffsetInMinutes = 5
        val expirationCheck = currentTime + milliseconds * 60 * expirationCheckTimeOffsetInMinutes
        if (requestContent.exp is String) {
            try {
                val expiration = requestContent.exp.toLong()
                if (expiration > expirationCheck) {
                    BaseLogger.log("Token is expired.")
                    return false
                }
            } catch (exception: NumberFormatException) {
                BaseLogger.error("exp claim is not a number")
                return false
            }
        } else {
            BaseLogger.log("exp is not present in SIOP Request.")
            return false
        }

        // 3. check the values on the parameters compared to the contents
        // They must be equal.
        val contentParams = requestContent.getOIDCParams()
        requestParameters.forEach { (param, values) ->
            if (contentParams[param] != values.first()) {
                // TODO(double check this logic with Guillermo)
                return false
            }
        }

        // All checks have passed so request is valid.
        return true
    }

    override fun formResponse(protector: Protector, collectedCredentials: List<Credential>): String {
        var responseBody: String
        val responseContent = createResponseContent(collectedCredentials)
        val serializedResponseContent = Serializer.stringify(OIDCResponseContent.serializer(), responseContent)
        val protectedToken: JwsToken = protector.protect(serializedResponseContent) as JwsToken
        val serializedToken = protectedToken.serialize(JwsFormat.Compact)
        responseBody = "id_token=${serializedToken}"
        if (!responseContent.state.isNullOrBlank()) {
            responseBody += "&state=${responseContent.state}"
        }
        return responseBody
    }

    /**
     * Create Response Content object from collectedCredentials and Request Contents.
     */
    private fun createResponseContent(collectedCredentials: List<Credential>): OIDCResponseContent {
        TODO("implement when protocol is finalized")
    }

}