package com.rowingclub.backend.service;

import com.rowingclub.backend.dto.UserDto;
import com.rowingclub.backend.entity.FinancialLedger;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.exception.ResourceNotFoundException;
import com.rowingclub.backend.repository.FinancialLedgerRepository;
import com.rowingclub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FinancialLedgerRepository ledgerRepository;
    private final PasswordEncoder passwordEncoder;

    private UserDto enrich(User user) {
        UserDto dto = UserDto.from(user);
        dto.setCreditBalance(ledgerRepository.calculateBalance(user.getId()));
        List<FinancialLedger> active = ledgerRepository.findActiveCreditsWithExpiration(
                user.getId(), LocalDateTime.now());
        if (!active.isEmpty()) {
            dto.setEarliestCreditExpiration(active.get(0).getExpirationDate());
        }
        return dto;
    }

    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return enrich(user);
    }

    public UserDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return enrich(user);
    }

    public List<UserDto> searchUsers(String query) {
        return userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query)
                .stream().map(this::enrich).toList();
    }

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream().map(this::enrich).toList();
    }

    public void incrementLessonsAttended(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setLessonsAttended(user.getLessonsAttended() + 1);
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public UserDto setBasicTrainingFinished(Long userId, boolean finished) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setIsFinishedBasicTraining(finished);
        userRepository.save(user);
        return enrich(user);
    }
}
