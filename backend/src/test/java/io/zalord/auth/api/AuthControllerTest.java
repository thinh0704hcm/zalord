package io.zalord.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.zalord.auth.api.controller.AuthController;
import io.zalord.auth.application.AuthService;
import io.zalord.auth.commands.RegisterCommand;
import io.zalord.auth.dto.request.LoginRequest;
import io.zalord.auth.dto.request.RegisterRequest;
import io.zalord.auth.dto.response.AuthResponse;
import io.zalord.common.exception.EmailAlreadyExistsException;
import io.zalord.common.exception.UserAlreadyExistsException;
import io.zalord.common.security.SecurityConfig;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
public class AuthControllerTest {

    private static final String VALID_PHONE = "0987654321";
    private static final String INVALID_PHONE = "invalidPhoneNumber";
    private static final String DUPLICATE_PHONE = "0999999999";
    private static final String RAW_PASSWORD = "mySecretPassword";
    private static final String INVALID_PASSWORD = "";
    private static final String VALID_FULL_NAME = "Full Name";
    private static final String INVALID_FULL_NAME = "";
    private static final String DUPLICATE_EMAIL = "duplicate@email.com";
    private static final String VALID_TOKEN = "BANANA";
    
    @MockitoBean
    private AuthService authService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    public AuthResponse validAuthResponse;

    @BeforeEach
    public void setUp() {
        validAuthResponse = new AuthResponse(VALID_TOKEN, UUID.randomUUID(), "Full Name", VALID_PHONE);
    }

    @Nested
    public class LoginTests {
        @Test
        @Tag("integration-auth-login")
        @DisplayName("AUTH-LOGIN-01: Should throw when phone number is invalid")
        public void login_shouldThrow_whenPhoneNumberIsInvalid() throws Exception {
            //Arrange
            LoginRequest invalidRequest = new LoginRequest(INVALID_PHONE, RAW_PASSWORD);

            mockMvc.perform(post("/api/auth/login")
                        .content(asJsonString(invalidRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.fields.phoneNumber").exists());
        }

        @Test
        @Tag("integration-auth-login")
        @DisplayName("AUTH-LOGIN-02: Should throw when password is invalid")
        public void login_shouldThrow_whenPasswordIsInvalid() throws Exception {
            //Arrange
            LoginRequest invalidRequest = new LoginRequest(VALID_PHONE, INVALID_PASSWORD);

            //Act & Assert
            mockMvc.perform(post("/api/auth/login")
                        .content(asJsonString(invalidRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.fields.password").exists());
        }

        @Test
        @Tag("integration-auth-login")
        @DisplayName("AUTH-LOGIN-03: Should return AuthResponse when credentials are valid")
        public void login_shouldReturnAuthResponse_whenCredentialsAreValid() throws Exception {
            //Arrange
            LoginRequest validRequest = new LoginRequest(VALID_PHONE, RAW_PASSWORD);

            when(authService.login(any(LoginRequest.class))).thenReturn(validAuthResponse);

            //Act & Assert
            mockMvc.perform(post("/api/auth/login")
                        .content(asJsonString(validRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.accessToken").exists())
                        .andExpect(jsonPath("$.fullName").value("Full Name"));

        }
    }

    @Nested
    public class RegisterTests {
        @Test
        @Tag("integration-auth-register")
        @DisplayName("AUTH-REG-01: Should throw when phone number is invalid")
        public void register_shouldThrow_whenPhoneNumberIsInvalid() throws Exception {
            //Arrange
            RegisterRequest invalidRequest = new RegisterRequest(INVALID_PHONE, RAW_PASSWORD, VALID_FULL_NAME, null, null, null);

            //Act & Assert
            mockMvc.perform(post("/api/auth/register")
                        .content(asJsonString(invalidRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.fields.phoneNumber").exists());
        }
    }

    @Test
    @Tag("integration_auth_register")
    @DisplayName("AUTH-REG-02: Should throw when password is invalid")
    public void register_shouldThrow_whenPasswordIsInvalid() throws Exception {
        //Arrange
        RegisterRequest invalidRequest = new RegisterRequest(VALID_PHONE, INVALID_PASSWORD, VALID_FULL_NAME, null, null, null);

        //Act & Assert
        mockMvc.perform(post("/api/auth/register")
                    .content(asJsonString(invalidRequest))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    @Tag("integration-auth-register")
    @DisplayName("AUTH-REG-03: Should throw when full name is invalid")
    public void register_shouldThrow_whenFullNameIsInvalid() throws Exception {
        //Arrange
        RegisterRequest invalidRequest = new RegisterRequest(VALID_PHONE, RAW_PASSWORD, INVALID_FULL_NAME, null, null, null);

        //Act & Assert
        mockMvc.perform(post("/api/auth/register")
                    .content(asJsonString(invalidRequest))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fields.fullName").exists());
    }

    @Test
    @Tag("integration-auth-register")
    @DisplayName("AUTH-REG-04: Should throw when phone number exists")
    public void register_shouldThrow_whenPhoneNumberExists() throws Exception {
        //Arrange
        RegisterRequest invalidRequest = new RegisterRequest(DUPLICATE_PHONE, RAW_PASSWORD, VALID_FULL_NAME, null, null, null);

        when(authService.register(any(RegisterCommand.class))).thenThrow(UserAlreadyExistsException.class);

        //Act & Assert
        mockMvc.perform(post("/api/auth/register")
                    .content(asJsonString(invalidRequest))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
    }

        @Test
    @Tag("integration-auth-register")
    @DisplayName("AUTH-REG-04: Should throw when phone number exists")
    public void register_shouldThrow_whenEmailExists() throws Exception {
        //Arrange
        RegisterRequest invalidRequest = new RegisterRequest(VALID_PHONE, RAW_PASSWORD, VALID_FULL_NAME, DUPLICATE_EMAIL, null, null);

        when(authService.register(any(RegisterCommand.class))).thenThrow(EmailAlreadyExistsException.class);

        //Act & Assert
        mockMvc.perform(post("/api/auth/register")
                    .content(asJsonString(invalidRequest))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @Tag("integration-auth-register")
    @DisplayName("AUTH-REG-05: Should return AuthResponse with valid credentials")
    public void register_shouldReturnAuthResponse_withValidCredentials() throws Exception {
        RegisterRequest validRequest = new RegisterRequest(VALID_PHONE, RAW_PASSWORD, VALID_FULL_NAME, null, null, null);

        when(authService.register(any(RegisterCommand.class))).thenReturn(validAuthResponse);

        mockMvc.perform(post("/api/auth/register")
                    .content(asJsonString(validRequest))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(VALID_TOKEN));
    }

    private String asJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }
}
