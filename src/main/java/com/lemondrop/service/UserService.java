package com.lemondrop.service;

import com.lemondrop.model.User;
import com.lemondrop.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> getByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User save(User user) {
        if (user.getId() == null) {
            // New user, hash password
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
            user.setCreatedAt(LocalDateTime.now());
            user.setActive(true);
        } else {
            // Existing user, check if password changed
            Optional<User> existing = userRepository.findById(user.getId());
            if (existing.isPresent()) {
                User oldUser = existing.get();
                if (!user.getPasswordHash().equals(oldUser.getPasswordHash())) {
                    user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
                }
                user.setCreatedAt(oldUser.getCreatedAt());
            }
        }
        return userRepository.save(user);
    }

    public void toggleActive(String id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setActive(!user.isActive());
            userRepository.save(user);
        });
    }

    public void updatePassword(String id, String rawPassword) {
        userRepository.findById(id).ifPresent(user -> {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            userRepository.save(user);
        });
    }
}
