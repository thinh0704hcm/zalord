package zalord.auth_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zalord.auth_service.config.RabbitMQConfig;
import zalord.auth_service.dto.event.UserCreatedEvent;
import zalord.auth_service.dto.response.LoginResponse;
import zalord.auth_service.dto.response.RefreshResponse;
import zalord.auth_service.dto.response.RegisterResponse;
import zalord.auth_service.exception.InvalidCredentialsException;
import zalord.auth_service.exception.PhoneNumberAlreadyExistsException;
import zalord.auth_service.grpc.UserGrpcClient;
import zalord.auth_service.jwt.JwtUtil;
import zalord.auth_service.model.CustomUserDetails;
import zalord.auth_service.model.OutboxEvent;
import zalord.auth_service.model.Role;
import zalord.auth_service.enums.RoleName;
import zalord.auth_service.model.Session;
import zalord.auth_service.model.User;
import zalord.auth_service.model.UserRoles;
import zalord.auth_service.repository.OutboxEventRepository;
import zalord.auth_service.repository.RoleRepository;
import zalord.auth_service.repository.UserRepository;
import zalord.auth_service.repository.UserRolesRepository;
import zalord.auth_service.service.IAuthService;
import zalord.auth_service.service.ISessionService;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AuthServiceImpl implements IAuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRolesRepository userRolesRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventRepository outboxEventRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final ISessionService sessionService;
    private final UserGrpcClient userGrpcClient;

    public AuthServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           UserRolesRepository userRolesRepository,
                           PasswordEncoder passwordEncoder,
                           OutboxEventRepository outboxEventRepository,
                           AuthenticationManager authenticationManager,
                           JwtUtil jwtUtil,
                           ObjectMapper objectMapper,
                           ISessionService sessionService,
                           UserGrpcClient userGrpcClient) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRolesRepository = userRolesRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxEventRepository = outboxEventRepository;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.userGrpcClient = userGrpcClient;
    }

    @Override
    @Transactional
    public RegisterResponse register(String displayName, String phoneNumber, String password) {
        return registerWithRole(displayName, phoneNumber, password, RoleName.USER);
    }

    @Override
    @Transactional
    public RegisterResponse createAdmin(String displayName, String phoneNumber, String password) {
        return registerWithRole(displayName, phoneNumber, password, RoleName.ADMIN);
    }

    @Override
    @Transactional
    public LoginResponse login(String phoneNumber, String password) {
        log.info("Login attempt for phoneNumber={}", phoneNumber);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(phoneNumber, password)
            );
        } catch (AuthenticationException ex) {
            log.warn("Login failed for phoneNumber={} reason={}", phoneNumber, ex.getMessage());
            throw new InvalidCredentialsException("Invalid phone number or password");
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String accessToken = jwtUtil.generateToken(userDetails);
        String refreshToken = sessionService.createSession(userDetails.getUser());
        log.info("Login success userId={} phoneNumber={}", userDetails.getUserId(), phoneNumber);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    @Transactional
    public RefreshResponse refresh(String refreshToken) {
        Session session = sessionService.validateRefreshToken(refreshToken);
        UUID userId = session.getUser().getId();
        List<String> roles = roleRepository.findRolesByUserId(userId);
        String accessToken = jwtUtil.generateAccessToken(userId, roles);
        log.info("Refreshed access token userId={}", userId);

        return RefreshResponse.builder()
                .accessToken(accessToken)
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        sessionService.revoke(refreshToken);
        log.info("Logout: refresh session revoked");
    }

    private RegisterResponse registerWithRole(String displayName,
                                              String phoneNumber,
                                              String password,
                                              RoleName roleName) {
        log.info("Register attempt phoneNumber={} role={}", phoneNumber, roleName);

        User user = createUser(phoneNumber, password);
        assignRole(user, roleName);

        // SYNC gRPC call to user-service to create the profile. Strong
        // consistency: if user-service can't create the profile, the entire
        // register transaction rolls back (caller sees 500). Trade-off: auth
        // requires user-service to be reachable. Chosen for register because
        // it's a low-volume flow where consistency matters more than coupling.
        userGrpcClient.createProfile(user.getId(), displayName, phoneNumber);

        log.info("Register success userId={} phoneNumber={} role={}", user.getId(), phoneNumber, roleName);
        return RegisterResponse.builder()
                .phoneNumber(user.getPhoneNumber())
                .displayName(displayName)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private User createUser(String phoneNumber, String password) {
        if (userRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            log.warn("Register rejected: phoneNumber={} already exists", phoneNumber);
            throw new PhoneNumberAlreadyExistsException(
                    "Phone number " + phoneNumber + " is already registered");
        }

        User user = new User();
        user.setPhoneNumber(phoneNumber);
        user.setPasswordHash(passwordEncoder.encode(password));

        return userRepository.save(user);
    }

    private void assignRole(User user, RoleName roleName) {
        Role role = roleRepository.findByName(roleName.name())
                .orElseThrow(() -> new IllegalStateException(
                        "Role " + roleName + " is not seeded in the database"));

        UserRoles userRole = new UserRoles();
        userRole.setUser(user);
        userRole.setRole(role);
        userRolesRepository.save(userRole);
    }

    private void createOutboxEvent(Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox event payload", ex);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setPayload(payload);
        outboxEvent.setRoutingKey(RabbitMQConfig.USER_CREATED_ROUTING_KEY);
        outboxEvent.setTopicExchange(RabbitMQConfig.USER_EXCHANGE);

        OutboxEvent saved = outboxEventRepository.save(outboxEvent);
        log.debug("Outbox event enqueued id={} routingKey={}", saved.getId(), saved.getRoutingKey());
    }
}
