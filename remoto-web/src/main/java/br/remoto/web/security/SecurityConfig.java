package br.remoto.web.security;

import br.remoto.web.config.RemotoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Autenticação sem banco de dados.
 *
 * <ul>
 *   <li><b>Produção</b> (qualquer perfil exceto "dev"): login via OIDC/OAuth2, configurado em
 *       application-prod.yml. O ReMoto não guarda senhas.</li>
 *   <li><b>Desenvolvimento</b> (perfil "dev"): formulário com usuários de teste do
 *       application-dev.yml.</li>
 * </ul>
 *
 * Convidados podem usar o site; o que exige conta é verificado nos controllers
 * ({@link Caller#requireLogin()}).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    private static void common(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.spa())
                .logout(logout -> logout.logoutSuccessUrl("/"));
    }

    @Bean
    @Profile("dev")
    SecurityFilterChain devSecurity(HttpSecurity http) throws Exception {
        common(http);
        http.formLogin(form -> form.defaultSuccessUrl("/", true));
        return http.build();
    }

    @Bean
    @Profile("dev")
    UserDetailsService devUsers(RemotoProperties props) {
        List<UserDetails> users = props.dev().users().stream()
                .map(u -> User.withUsername(u.username()).password(u.password()).roles("USER").build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    @Profile("!dev")
    SecurityFilterChain oidcSecurity(HttpSecurity http) throws Exception {
        common(http);
        http.oauth2Login(oauth -> oauth.defaultSuccessUrl("/", true));
        return http.build();
    }
}
