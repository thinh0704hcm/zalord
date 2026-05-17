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
import zalord.auth_service.dto.response.RegisterResponse;
import zalord.auth_service.exception.InvalidCredentialsException;
import zalord.auth_service.exception.PhoneNumberAlreadyExistsException;
import zalord.auth_service.jwt.JwtUtil;
import zalord.auth_service.model.CustomUserDetails;
import zalord.auth_service.model.OutboxEvent;
import zalord.auth_service.model.Role;
import zalord.auth_service.enums.RoleName;
import zalord.auth_service.model.User;
import zalord.auth_service.model.UserRoles;
import zalord.auth_service.repository.OutboxEventRepository;
import zalord.auth_service.repository.RoleRepository;
import zalord.auth_service.repository.UserRepository;
import zalord.auth_service.repository.UserRolesRepository;
import zalord.auth_service.service.IAuthService;

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

    public AuthServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           UserRolesRepository userRolesRepository,
                           PasswordEncoder passwordEncoder,
                           OutboxEventRepository outboxEventRepository,
                           AuthenticationManager authenticationManager,
                           JwtUtil jwtUtil,
                           ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRolesRepository = userRolesRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxEventRepository = outboxEventRepository;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
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
        String token = jwtUtil.generateToken(userDetails);
        log.info("Login success userId={} phoneNumber={}", userDetails.getUserId(), phoneNumber);

        return LoginResponse.builder()
                .accessToken(token)
                .build();
    }

    private RegisterResponse registerWithRole(String displayName,
                                              String phoneNumber,
                                              String password,
                                              RoleName roleName) {
        log.info("Register attempt phoneNumber={} role={}", phoneNumber, roleName);

        User user = createUser(phoneNumber, password);
        assignRole(user, roleName);

        UserCreatedEvent event = new UserCreatedEvent(user.getId(), displayName);
        createOutboxEvent(event);

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
