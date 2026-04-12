package io.zalord.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.zalord.auth.commands.RegisterCommand;
import io.zalord.auth.dto.request.LoginRequest;
import io.zalord.auth.dto.response.AuthResponse;
import io.zalord.auth.model.Credential;
import io.zalord.auth.repository.CredentialRepository;
import io.zalord.common.exception.EmailAlreadyExistsException;
import io.zalord.common.exception.InvalidCredentialsException;
import io.zalord.common.exception.UserAlreadyExistsException;
import io.zalord.common.security.JwtService;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    private static final UUID VALID_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String VALID_PHONE = "0987654321";
    private static final String INVALID_PHONE = "0000000000";
    private static final String RAW_PASSWORD = "mySecretPassword";
    private static final String WRONG_PASSWORD = "wrongPassword";
    private static final String HASHED_PASSWORD = "hashed_mySecretPassword";
    private static final String VALID_TOKEN = "BANANA";
    private static final String INVALID_EMAIL = "banana@gmail.com";
    private static final String VALID_FULL_NAME = "myName";
    
    @Mock private CredentialRepository credentialRepository;

    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private JwtService jwtService;

    @Mock private PasswordEncoder passwordEncoder;

    private AuthService authService;

    private Credential validCredential;


    @BeforeEach
    void setUp() {
        validCredential = new Credential();
        validCredential.setUserId(VALID_UUID);
        validCredential.setPhoneNumber(VALID_PHONE);
        validCredential.setPasswordHash(HASHED_PASSWORD);

        authService = new AuthService(credentialRepository, jwtService, passwordEncoder, eventPublisher);
    }

    @Nested
    public class LoginTests {
        @Test
        @Tag("unit-auth-login")
        @DisplayName("AUTH-LOGIN-01: Should throw when phone number is not found")
        public void login_shouldThrow_whenPhoneNumberNotFound() {
            //Arrange
            LoginRequest invalidRequest = new LoginRequest(INVALID_PHONE, WRONG_PASSWORD);

            when(credentialRepository.findByPhoneNumber(INVALID_PHONE))
                .thenReturn(Optional.empty());

            //Act & Assert
            assertThrows(InvalidCredentialsException.class,
                () -> authService.login(invalidRequest));

            //Verify
            //assertions
            verify(credentialRepository).findByPhoneNumber(anyString());
            verifyNoInteractions(passwordEncoder, jwtService);
        }

        @Test
        @Tag("unit-auth-login")
        @DisplayName("AUTH-LOGIN-02: Should throw when password is incorrect")
        public void login_shouldThrow_whenPasswordIsIncorrect() {
            //Arrange
            LoginRequest invalidRequest = new LoginRequest(VALID_PHONE,WRONG_PASSWORD);

            when(credentialRepository.findByPhoneNumber(anyString()))
                .thenReturn(Optional.of(validCredential));
            when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(false);

            //Act & Assert
            assertThrows(InvalidCredentialsException.class,
                () -> authService.login(invalidRequest));

            //Verify
            //assertions
            verify(credentialRepository).findByPhoneNumber(VALID_PHONE);
            verify(passwordEncoder).matches(WRONG_PASSWORD, HASHED_PASSWORD);

            verifyNoInteractions(jwtService);
        }

        @Test
        @Tag("unit-auth-login")
        @DisplayName("AUTH-LOGIN-03: Should return AuthResponse when credentials are valid.")
        public void login_shouldReturnAuthResponse_whenCredentialsAreValid() {
            //Arrange
            LoginRequest validRequest = new LoginRequest(VALID_PHONE, RAW_PASSWORD);

            when(credentialRepository.findByPhoneNumber(anyString()))
                .thenReturn(Optional.of(validCredential));
            when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
            when(jwtService.generateToken(any(UUID.class), any()))
                .thenReturn(VALID_TOKEN);

            //Act
            AuthResponse authResponse = authService.login(validRequest);
            //Assert
            assertEquals(VALID_TOKEN, authResponse.token());

            //Verify
            verify(credentialRepository).findByPhoneNumber(VALID_PHONE);
            verify(passwordEncoder).matches(RAW_PASSWORD, HASHED_PASSWORD);
            verify(jwtService).generateToken(eq(validCredential.getUserId()), ArgumentMatchers.<Map<String, Object>>any());
        }
    }

    @Nested
    public class RegisterTests {
        @Test
        @Tag("unit-auth-register")
        @DisplayName("AUTH-REG-01: Should throw on duplicate phone number.")
        public void register_shouldThrow_whenPhoneNumberAlreadyExists () {
            //Arrange
            RegisterCommand invalidCommand = new RegisterCommand(INVALID_PHONE, RAW_PASSWORD, VALID_FULL_NAME, null, null, null);

            when(credentialRepository.existsByPhoneNumber(anyString())).thenReturn(true);

            //Act & Assert
            assertThrows(UserAlreadyExistsException.class, () -> authService.register(invalidCommand));

            //Verify
            verify(credentialRepository).existsByPhoneNumber(INVALID_PHONE);
            verifyNoInteractions(passwordEncoder, jwtService);
        }

        @Test
        @Tag("unit-auth-register")
        @DisplayName("AUTH-REG-02: Should throw on duplicate email.")
        public void register_shouldThrow_whenEmailAlreadyExists() {
            //Arrange
            RegisterCommand invalidCommand = new RegisterCommand(VALID_PHONE, RAW_PASSWORD, VALID_FULL_NAME, INVALID_EMAIL, null, null);

            when(credentialRepository.existsByPhoneNumber(anyString())).thenReturn(false);
            when(credentialRepository.existsByEmail(anyString())).thenReturn(true);

            //Act & Assert
            assertThrows(EmailAlreadyExistsException.class, () -> authService.register(invalidCommand));

            //Verify
            verify(credentialRepository).existsByEmail(INVALID_EMAIL);
            verifyNoInteractions(jwtService, passwordEncoder);
        }

        @Test
        @Tag("unit-auth-register")
        @DisplayName("AUTH-REG-03: Should return AuthResponse on register success.")
        public void register_shouldReturnAuthResponse_whenRegisterSuccess() {
            //Arrange
            RegisterCommand validCommand = new RegisterCommand(VALID_PHONE, RAW_PASSWORD, VALID_FULL_NAME, null, null, null);
            ArgumentCaptor<Credential> credentialCaptor = ArgumentCaptor.forClass(Credential.class);

            when(credentialRepository.existsByPhoneNumber(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn(HASHED_PASSWORD);
            when(credentialRepository.save(any(Credential.class))).thenReturn(validCredential);
            when(jwtService.generateToken(any(UUID.class), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(VALID_TOKEN);

            //Act
            AuthResponse response = authService.register(validCommand);

            //Assert
            assertEquals(response.token(), VALID_TOKEN);

            //Verify
            verify(credentialRepository).save(credentialCaptor.capture());
            UUID capturedId = credentialCaptor.getValue().getUserId();
            
            verify(credentialRepository).existsByPhoneNumber(VALID_PHONE);
            verify(passwordEncoder).encode(RAW_PASSWORD);
            verify(jwtService).generateToken(eq(capturedId), ArgumentMatchers.<Map<String, Object>>any());
        }
    }
}
