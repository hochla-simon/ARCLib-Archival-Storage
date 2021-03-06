package cz.cas.lib.arcstorage.security;

import cz.cas.lib.arcstorage.security.basic.PathBasicAuthFilter;
import cz.cas.lib.arcstorage.security.service.UserDetailServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.inject.Inject;
import javax.servlet.Filter;
import java.util.ArrayList;
import java.util.List;

import static cz.cas.lib.arcstorage.util.Utils.asArray;

@Configuration
public class SecurityInitializer extends BaseSecurityInitializer {

    private UserDetailServiceImpl userDetailsService;

    private PasswordEncoder encoder;

    private String[] basicAuthQueries;

    @Override
    protected AuthenticationProvider[] primaryAuthProviders() {
        List<AuthenticationProvider> providers = new ArrayList<>();

        DaoAuthenticationProvider userAuthProvider = new DaoAuthenticationProvider();
        userAuthProvider.setPasswordEncoder(encoder);
        userAuthProvider.setUserDetailsService(userDetailsService);
        providers.add(userAuthProvider);

        return providers.toArray(new AuthenticationProvider[providers.size()]);
    }

    @Override
    protected Filter[] primarySchemeFilters() throws Exception {
        PathBasicAuthFilter basicFilter = new PathBasicAuthFilter(authenticationManager(), basicAuthQueries);
        return asArray(basicFilter);
    }

    @Inject
    public void setEncoder(PasswordEncoder encoder) {
        this.encoder = encoder;
    }


    @Inject
    public void setUserDetailsService(UserDetailServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Inject
    public void setBasicAuthQuery(@Value("${security.basic.authQueries}") String[] basicAuthQueries) {
        this.basicAuthQueries = basicAuthQueries;
    }
}
