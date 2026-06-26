package zalord.auth_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import zalord.auth_service.dto.response.LoginResponse;
import zalord.auth_service.dto.response.RegisterResponse;
import zalord.auth_service.enums.RoleName;
import zalord.auth_service.exception.InvalidCredentialsException;
import zalord.auth_service.exception.PhoneNumberAlreadyExistsException;
import zalord.auth_service.grpc.UserGrpcClient;
import zalord.auth_service.jwt.JwtUtil;
import zalord.auth_service.model.CustomUserDetails;
import zalord.auth_service.model.Role;
import zalord.auth_service.model.User;
import zalord.auth_service.repository.OutboxEventRepository;
import zalord.auth_service.repository.RoleRepository;
import zalord.auth_service.repository.UserRepository;
import zalord.auth_service.repository.UserRolesRepository;
import zalord.auth_service.service.ISessionService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRolesRepository userRolesRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ISessionService sessionService;
    @Mock
    private UserGrpcClient userGrpcClient;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setPhoneNumber("0987654321");
        testUser.setPasswordHash("hashedPassword");
        testUser.setCreatedAt(Instant.now());

        userDetails = new CustomUserDetails(testUser, roleRepository);
    }

    @Test
    void register_Success() {
        when(userRepository.findByPhoneNumber(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        Role role = new Role();
        role.setName(RoleName.USER.name());
        when(roleRepository.findByName(RoleName.USER.name())).thenReturn(Optional.of(role));

        RegisterResponse response = authService.register("Test User", "0987654321", "password123");

        assertNotNull(response);
        assertEquals("0987654321", response.getPhoneNumber());
        assertEquals("Test User", response.getDisplayName());
        
        verify(userRepository).save(any(User.class));
        verify(userRolesRepository).save(any());
        verify(userGrpcClient).createProfile(testUser.getId(), "Test User", "0987654321");
    }

    @Test
    void register_Failure_PhoneNumberExists() {
        when(userRepository.findByPhoneNumber("0987654321")).thenReturn(Optional.of(testUser));

        assertThrows(PhoneNumberAlreadyExistsException.class, () -> {
            authService.register("Test User", "0987654321", "password123");
        });

        verify(userRepository, never()).save(any(User.class));
        verify(userGrpcClient, never()).createProfile(any(), any(), any());
    }

    @Test
    void login_Success() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        
        when(jwtUtil.generateToken(userDetails)).thenReturn("accessToken123");
        when(sessionService.createSession(testUser)).thenReturn("refreshToken123");

        LoginResponse response = authService.login("0987654321", "password123");

        assertNotNull(response);
        assertEquals("accessToken123", response.getAccessToken());
        assertEquals("refreshToken123", response.getRefreshToken());
    }

    @Test
    void login_Failure_InvalidCredentials() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(InvalidCredentialsException.class, () -> {
            authService.login("0987654321", "wrongpassword");
        });
    }
}
