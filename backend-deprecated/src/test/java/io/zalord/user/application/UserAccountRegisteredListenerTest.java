package io.zalord.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.zalord.common.events.AccountRegisteredEvent;
import io.zalord.user.domain.entities.User;
import io.zalord.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserAccountRegisteredListenerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PHONE_NUMBER = "0987654321";
    private static final String EMAIL = "user@email.com";

    @Mock
    private UserRepository userRepository;

    private UserAccountRegisteredListener listener;

    @BeforeEach
    void setUp() {
        listener = new UserAccountRegisteredListener(userRepository);
    }

    @Test
    @Tag("unit-user")
    @DisplayName("USER-REG-01: Should create an empty user profile shell from account registration")
    void onAccountRegistered_shouldCreateEmptyShellProfile() {
        AccountRegisteredEvent event = new AccountRegisteredEvent(USER_ID, PHONE_NUMBER, EMAIL);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        listener.onAccountRegistered(event);

        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals(USER_ID, savedUser.getId());
        assertEquals(PHONE_NUMBER, savedUser.getPhoneNumber());
        assertEquals(EMAIL, savedUser.getEmail());
        assertNull(savedUser.getFullName());
        assertNull(savedUser.getBirthDate());
        assertNull(savedUser.getGender());
    }
}
