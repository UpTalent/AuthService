package io.github.uptalent.auth;

import feign.FeignException;
import io.github.uptalent.auth.client.AccountClient;
import io.github.uptalent.auth.exception.BlockedAccountException;
import io.github.uptalent.auth.jwt.JwtService;
import io.github.uptalent.auth.model.request.AuthLogin;
import io.github.uptalent.auth.model.response.AuthResponse;
import io.github.uptalent.auth.service.*;
import io.github.uptalent.starter.model.response.JwtResponse;
import io.github.uptalent.starter.security.JwtBlacklistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;

import static io.github.uptalent.auth.utils.MockModelsUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    @Mock
    private JwtService jwtService;
    @Mock
    private AccountClient accountClient;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private AuthorizedAccountService authorizedAccountService;
    @Mock
    private EmailProducerService emailProducerService;
    @Mock
    private AccountVerifyService accountVerifyService;
    @Mock
    private JwtBlacklistService jwtBlacklistService;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Log in successfully")
    void loginSuccessfully() {
        AuthLogin authLogin = generateAuthLogin();
        AuthResponse authResponse = generateAuthResponse();
        String email = authLogin.getEmail();

        when(accountClient.login(authLogin)).thenReturn(authResponse);
        when(jwtService.generateToken(authResponse)).thenReturn("token");

        JwtResponse jwtResponse = authService.loginAccount(authLogin);

        assertEquals(jwtResponse.getJwt(), "token");

        verify(accountClient).login(authLogin);
        verify(loginAttemptService).evictEmailFromAttempts(email);
        verify(authorizedAccountService).saveAuthorizedAccountByEmail(email);
        verify(jwtService).generateToken(authResponse);
    }

    @Test
    @DisplayName("Log in when account is blocked")
    void loginWhenAccountIsBlocked() {
        AuthLogin authLogin = generateAuthLogin();
        String email = authLogin.getEmail();

        when(redisTemplate.hasKey(BLOCKED_ACCOUNT + email)).thenReturn(true);

        assertThrows(BlockedAccountException.class, () -> authService.loginAccount(authLogin));
    }

    @Test
    @DisplayName("Log in when account is not verified")
    void loginWhenAccountIsNotVerified() {
        AuthLogin authLogin = generateAuthLogin();
        String email = authLogin.getEmail();

        when(accountVerifyService.existsByEmail(email)).thenReturn(true);

        assertThrows(BadCredentialsException.class, () -> authService.loginAccount(authLogin));
    }

    @Test
    @DisplayName("Log in when already authorized")
    void loginWhenAlreadyAuthorized() {
        AuthLogin authLogin = generateAuthLogin();
        String email = authLogin.getEmail();

        when(authorizedAccountService.isAuthorizedAccountByEmail(email)).thenReturn(true);

        assertThrows(BadCredentialsException.class, () -> authService.loginAccount(authLogin));
    }

    @Test
    @DisplayName("Log in with max reached attempts")
    void loginWithMaxReachedAttempts() {
        AuthLogin authLogin = generateAuthLogin();
        String email = authLogin.getEmail();

        when(loginAttemptService.isReachedMaxAttempts(email)).thenReturn(true);

        assertThrows(BadCredentialsException.class, () -> authService.loginAccount(authLogin));
    }

    @Test
    @DisplayName("Log in with bad credentials")
    void loginWithBadCredentials() {
        AuthLogin authLogin = generateAuthLogin();
        String email = authLogin.getEmail();

        FeignException.Unauthorized unauthorized = mock(FeignException.Unauthorized.class);

        when(accountClient.login(authLogin)).thenThrow(unauthorized);

        assertThrows(NullPointerException.class, () -> authService.loginAccount(authLogin));
        verify(loginAttemptService).incrementAttemptByEmail(email);
    }

    @Test
    @DisplayName("Log out successfully")
    void logoutSuccessfully() {
        String accessToken = "token";
        String email = "test@email.com";

        when(jwtService.getEmailFromToken(accessToken)).thenReturn(email);

        authService.logout(accessToken);

        verify(jwtBlacklistService).addToBlacklist(accessToken);
        verify(authorizedAccountService).evictAuthorizedAccountByEmail(email);
    }
}
