package com.analytics.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the authentication provider.
 *
 * Set {@code app.auth.provider} to {@code password} for local/dev password-based auth,
 * or {@code oidc} to delegate authentication to an external identity provider (e.g. Okta,
 * Azure AD, Google Workspace) that issues OAuth 2.0 / OIDC JWTs.
 *
 * When using OIDC, the dashboard does not manage credentials — it validates incoming
 * JWTs against the IdP's JWKS endpoint and extracts user identity from token claims.
 */
@Configuration
@ConfigurationProperties(prefix = "app.auth")
public class IdentityProviderConfig {

    /**
     * Which authentication provider to use: {@code password} or {@code oidc}.
     */
    private AuthProvider provider = AuthProvider.PASSWORD;

    /**
     * OIDC / OAuth 2.0 identity provider settings. Only used when {@code provider = oidc}.
     */
    private OidcConfig oidc = new OidcConfig();

    public enum AuthProvider {
        PASSWORD, OIDC
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public void setProvider(AuthProvider provider) {
        this.provider = provider;
    }

    public OidcConfig getOidc() {
        return oidc;
    }

    public void setOidc(OidcConfig oidc) {
        this.oidc = oidc;
    }

    public boolean isOidc() {
        return provider == AuthProvider.OIDC;
    }

    /**
     * OIDC identity provider connection settings.
     */
    public static class OidcConfig {

        /**
         * The IdP issuer URI (e.g. {@code https://login.microsoftonline.com/{tenant}/v2.0}).
         * Used to validate the {@code iss} claim in incoming JWTs.
         */
        private String issuerUri;

        /**
         * JWKS (JSON Web Key Set) endpoint for verifying token signatures.
         * If not set, it will be auto-discovered from {@code issuerUri + /.well-known/openid-configuration}.
         */
        private String jwksUri;

        /**
         * The OAuth 2.0 client ID registered with the IdP.
         */
        private String clientId;

        /**
         * Expected {@code aud} (audience) claim value. Typically the client ID.
         */
        private String audience;

        /**
         * Mapping between IdP JWT claims and the fields this application expects.
         */
        private ClaimsMapping claims = new ClaimsMapping();

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getJwksUri() {
            return jwksUri;
        }

        public void setJwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public ClaimsMapping getClaims() {
            return claims;
        }

        public void setClaims(ClaimsMapping claims) {
            this.claims = claims;
        }
    }

    /**
     * Maps IdP-specific JWT claim names to the claim names this application uses internally.
     * Different identity providers may use different claim names for the same concept.
     * For example, Azure AD uses {@code oid} for user ID while others use {@code sub}.
     */
    public static class ClaimsMapping {

        /** Claim that contains the user's unique identifier. Default: {@code sub}. */
        private String userId = "sub";

        /** Claim that contains the user's roles. Default: {@code roles}. */
        private String roles = "roles";

        /** Claim that contains the organization ID. Default: {@code org_id}. */
        private String orgId = "org_id";

        /** Claim that contains the user's team IDs. Default: {@code teams}. */
        private String teams = "teams";

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getRoles() {
            return roles;
        }

        public void setRoles(String roles) {
            this.roles = roles;
        }

        public String getOrgId() {
            return orgId;
        }

        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }

        public String getTeams() {
            return teams;
        }

        public void setTeams(String teams) {
            this.teams = teams;
        }
    }
}
