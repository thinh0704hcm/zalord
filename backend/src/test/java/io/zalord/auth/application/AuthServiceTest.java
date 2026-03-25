package io.zalord.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.zalord.auth.api.AuthResponse;
import io.zalord.auth.api.LoginRequest;
import io.zalord.auth.api.RegisterRequest;
import io.zalord.auth.domain.User;
import io.zalord.auth.infrastructure.UserRepository;
import io.zalord.common.exception.EmailAlreadyExistsException;
import io.zalord.common.exception.InvalidCredentialsException;
import io.zalord.common.exception.UserAlreadyExistsException;
import io.zalord.common.security.JwtService;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    private static final String VALID_PHONE = "0987654321";
    private static final String INVALID_PHONE = "0000000000";
    private static final String RAW_PASSWORD = "mySecretPassword";
    private static final String WRONG_PASSWORD = "wrongPassword";
    private static final String HASHED_PASSWORD = "hashed_mySecretPassword";
    private static final String VALID_TOKEN = "BANANA";
    private static final String INVALID_EMAIL = "banana@gmail.com";
    
    @Mock private UserRepository userRepository;

    @Mock private JwtService jwtService;

    @Mock private PasswordEncoder passwordEncoder;

    private AuthService authService;

    private User validUser;


    @BeforeEach
    void setUp() {
        validUser = new User();
        validUser.setPhoneNumber(VALID_PHONE);
        validUser.setPasswordHash(HASHED_PASSWORD);

        authService = new AuthService(userRepository, jwtService, passwordEncoder);

    }

    @Nested
    public class LoginTests {
        @Test
        @Tag("unit-auth-login")
        @DisplayName("AUTH-LOGIN-01: Should throw when phone number is not found")
        public void login_shouldThrow_whenPhoneNumberNotFound() {
            //Arrange
            LoginRequest invalidRequest = new LoginRequest();
            invalidRequest.setPhoneNumber(INVALID_PHONE);
            invalidRequest.setPassword(WRONG_PASSWORD);

            when(userRepository.findByPhoneNumber(INVALID_PHONE))
                .thenReturn(Optional.empty());

            //Act & Assert
            assertThrows(InvalidCredentialsException.class,
                () -> authService.login(invalidRequest));

            //Verify
            //assertions
            verify(userRepository).findByPhoneNumber(anyString());
            verifyNoInteractions(passwordEncoder, jwtService);
        }

        @Test
        @Tag("unit-auth-login")
        @DisplayName("AUTH-LOGIN-02: Should throw when password is incorrect")
        public void login_shouldThrow_whenPasswordIsIncorrect() {
            //Arrange
            LoginRequest invalidRequest = new LoginRequest();
            invalidRequest.setPhoneNumber(VALID_PHONE);
            invalidRequest.setPassword(WRONG_PASSWORD);

            when(userRepository.findByPhoneNumber(anyString()))
                .thenReturn(Optional.of(validUser));
            when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(false);

            //Act & Assert
            assertThrows(InvalidCredentialsException.class,
                () -> authService.login(invalidRequest));

            //Verify
            //assertions
            verify(userRepository).findByPhoneNumber(VALID_PHONE);
            verify(passwordEncoder).matches(WRONG_PASSWORD, HASHED_PASSWORD);

            verifyNoInteractions(jwtService);
        }

        @Test
        @Tag("unit-auth-login")
        @DisplayName("AUTH-LOGIN-03: Should return AuthResponse when credentials are valid.")
        public void login_shouldReturnAuthResponse_whenCredentialsAreValid() {
            //Arrange
            LoginRequest validRequest = new LoginRequest();
            validRequest.setPhoneNumber(VALID_PHONE);
            validRequest.setPassword(RAW_PASSWORD);

            when(userRepository.findByPhoneNumber(anyString()))
                .thenReturn(Optional.of(validUser));
            when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
            when(jwtService.generateToken(any(User.class)))
                .thenReturn(VALID_TOKEN);

            //Act
            AuthResponse authResponse = authService.login(validRequest);
            //Assert
            assertEquals(VALID_TOKEN, authResponse.getAccessToken());

            //Verify
            verify(userRepository).findByPhoneNumber(VALID_PHONE);
            verify(passwordEncoder).matches(RAW_PASSWORD, HASHED_PASSWORD);
            verify(jwtService).generateToken(validUser);
        }
    }

    @Nested
    public class RegisterTests {
        @Test
        @Tag("unit-auth-register")
        @DisplayName("AUTH-REG-01: Should throw on duplicate phone number.")
        public void register_shouldThrow_whenPhoneNumberAlreadyExists () {
            //Arrange
            RegisterRequest invalidRequest = new RegisterRequest();
            invalidRequest.setPhoneNumber(INVALID_PHONE);
            invalidRequest.setPassword(RAW_PASSWORD);

            when(userRepository.existsByPhoneNumber(anyString())).thenReturn(true);

            //Act & Assert
            assertThrows(UserAlreadyExistsException.class, () -> authService.register(invalidRequest));

            //Verify
            verify(userRepository).existsByPhoneNumber(INVALID_PHONE);
            verifyNoInteractions(passwordEncoder, jwtService);
        }

        @Test
        @Tag("unit-auth-register")
        @DisplayName("AUTH-REG-02: Should throw on duplicate email.")
        public void register_shouldThrow_whenEmailAlreadyExists() {
            //Arrange
            RegisterRequest invalidRequest = new RegisterRequest();
            invalidRequest.setPhoneNumber(VALID_PHONE);
            invalidRequest.setPassword(RAW_PASSWORD);
            invalidRequest.setEmail(INVALID_EMAIL);

            when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(true);

            //Act & Assert
            assertThrows(EmailAlreadyExistsException.class, () -> authService.register(invalidRequest));

            //Verify
            verify(userRepository).existsByEmail(INVALID_EMAIL);
            verify(passwordEncoder).encode(RAW_PASSWORD);
            verifyNoInteractions(jwtService);
        }

        @Test
        @Tag("unit-auth-register")
        @DisplayName("AUTH-REG-03: Should return AuthResponse on register success.")
        public void register_shouldReturnAuthResponse_whenRegisterSuccess() {
            //Arrange
            RegisterRequest validRequest = new RegisterRequest();
            validRequest.setPhoneNumber(VALID_PHONE);
            validRequest.setPassword(RAW_PASSWORD);

            when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn(HASHED_PASSWORD);
            when(userRepository.saveAndFlush(any(User.class))).thenReturn(validUser);
            when(jwtService.generateToken(any(User.class))).thenReturn(VALID_TOKEN);

            //Act
            AuthResponse response = authService.register(validRequest);

            //Assert
            assertEquals(response.getAccessToken(), VALID_TOKEN);

            //Verify
            verify(userRepository).existsByPhoneNumber(VALID_PHONE);
            verify(passwordEncoder).encode(RAW_PASSWORD);
            verify(jwtService).generateToken(validUser);
        }
    }
}
