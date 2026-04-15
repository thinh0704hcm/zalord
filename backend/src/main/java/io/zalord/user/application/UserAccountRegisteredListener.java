package io.zalord.user.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.zalord.common.events.AccountRegisteredEvent;
import io.zalord.user.domain.entities.User;
import io.zalord.user.repository.UserRepository;

@Component
public class UserAccountRegisteredListener {

    private final UserRepository userRepository;

    public UserAccountRegisteredListener(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @EventListener
    public void onAccountRegistered(AccountRegisteredEvent event) {
        User user = new User();
        user.setId(event.userId());
        user.setPhoneNumber(event.phoneNumber());
        user.setEmail(event.email());
        userRepository.save(user);
    }
}
