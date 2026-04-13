package io.zalord.user.application;

import io.zalord.user.domain.entities.User;
import io.zalord.user.repository.UserRepository;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.zalord.common.events.UserRegisteredEvent;

@Component
public class UserEventListener {

    private final UserRepository userRepository;

    public UserEventListener(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        User user = new User();
        user.setId(event.id());
        user.setPhoneNumber(event.phoneNumber());
        user.setEmail(event.email());
        user.setFullName(event.fullName());
        user.setBirthDate(event.birthDate());
        user.setGender(event.gender());
        userRepository.save(user);
    }
}
