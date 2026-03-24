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

import io.zalord.auth.application.AuthService;
import io.zalord.common.security.SecurityConfig;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
public class AuthControllerTest {

    private static final String VALID_PHONE = "0987654321";
    private static final String INVALID_PHONE = "invalidPhoneNumber";
    private static final String RAW_PASSWORD = "mySecretPassword";
    private static final String INVALID_PASSWORD = "";
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
        @Tag("unit-auth-login")
        @DisplayName("AUTH-LOGIN-01: Should throw when phone number is invalid")
        public void login_shouldThrow_whenPhoneNumberIsInvalid() throws Exception {
            //Arrange
            LoginRequest invalidRequest = new LoginRequest();
            invalidRequest.setPhoneNumber(INVALID_PHONE);
            invalidRequest.setPassword(RAW_PASSWORD);

            mockMvc.perform(post("/api/auth/login")
                        .content(asJsonString(invalidRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.fields.phoneNumber").exists());
        }

        @Test
        @Tag("unit-auth-login")
        @DisplayName("AUTH-LOGIN-02: Should throw when password is incorrect")
        public void login_shouldThrow_whenPasswordIsBlank() throws Exception {
            //Arrange
            LoginRequest invalidRequest = new LoginRequest();
            invalidRequest.setPhoneNumber(VALID_PHONE);
            invalidRequest.setPassword(INVALID_PASSWORD);

            //Act & Assert
            mockMvc.perform(post("/api/auth/login")
                        .content(asJsonString(invalidRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.fields.password").exists());
        }

        @Test
        @Tag("unit-auth-login")
        @DisplayName("AUTH-LOGIN-03: shouldReturnAuthResponse_whenCredentialsAreValid")
        public void login_shouldReturnAuthResponse_whenCredentialsAreValid() throws Exception {
            //Arrange
            LoginRequest validRequest = new LoginRequest();
            validRequest.setPhoneNumber(VALID_PHONE);
            validRequest.setPassword(RAW_PASSWORD);

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

    private String asJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }
}
