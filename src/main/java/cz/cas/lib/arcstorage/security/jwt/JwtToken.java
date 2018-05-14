package cz.cas.lib.arcstorage.security.jwt;

import cz.cas.lib.arcstorage.security.user.UserDetails;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Spring {@link org.springframework.security.core.Authentication} implementation for JWT tokens.
 *
 * <p>
 *     <a href="https://jwt.io">JWT</a> tokens are represented as {@link Claims}. Claims are a simple list
 *     of named facts about user. When the {@link JwtToken} is generated by the server, it is cryptographically
 *     signed, so malicious user can not tamper with it.
 * </p>
 * <p>
 *     Most of the time, {@link Claims} contains username, creation date, expiration date and roles or permissions.
 *     But can also contains the department name or another attributes which are essential.
 * </p>
 */
public class JwtToken extends AbstractAuthenticationToken {
    private final Object principal;
    private Claims claims;

    public JwtToken(String token) {
        super(null);
        this.claims = null;
        this.principal = token;
        setAuthenticated(false);
    }

    public JwtToken(UserDetails principal, Claims claims, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.claims = claims;
        setAuthenticated(true);
        setDetails(principal);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public UserDetails getDetails() {
        return (UserDetails) super.getDetails();
    }

    public Claims getClaims() {
        return claims;
    }

    public void setClaims(Claims claims) {
        this.claims = claims;
    }
}
