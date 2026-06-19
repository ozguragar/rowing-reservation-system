package com.rowingclub.backend.service;

import com.rowingclub.backend.dto.UserDto;
import com.rowingclub.backend.entity.FinancialLedger;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.enums.MemberType;
import com.rowingclub.backend.enums.Role;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.exception.ResourceNotFoundException;
import com.rowingclub.backend.repository.FinancialLedgerRepository;
import com.rowingclub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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

    public List<UserDto> searchUsers(Long clubId, String query) {
        if (clubId == null) return searchUsers(query);
        return userRepository.searchByClubId(clubId, query)
                .stream().map(this::enrich).toList();
    }

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream().map(this::enrich).toList();
    }

    public List<UserDto> getAllUsers(Long clubId) {
        if (clubId == null) return getAllUsers();
        return userRepository.findByClubId(clubId).stream().map(this::enrich).toList();
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

    /**
     * Admin update of a user's role and/or member type, with guardrails:
     * <ul>
     *   <li>You cannot edit your own role/type.</li>
     *   <li>A non-SUPERADMIN actor may only edit users in their own club.</li>
     *   <li>Only a SUPERADMIN may grant the SUPERADMIN role.</li>
     * </ul>
     * Null fields are left unchanged.
     */
    @Transactional
    public UserDto updateRoleAndType(Long targetId, String role, String memberType, String actingEmail) {
        User actor = userRepository.findByEmail(actingEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (actor.getId().equals(target.getId())) {
            throw new BusinessException("You cannot change your own role or member type");
        }

        boolean actorIsSuperadmin = actor.getRole() == Role.SUPERADMIN;
        if (!actorIsSuperadmin) {
            Long actorClub = actor.getClub() != null ? actor.getClub().getId() : null;
            Long targetClub = target.getClub() != null ? target.getClub().getId() : null;
            if (actorClub == null || !actorClub.equals(targetClub)) {
                throw new AccessDeniedException("You can only manage members of your own club");
            }
        }

        if (role != null && !role.isBlank()) {
            Role newRole;
            try {
                newRole = Role.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Unknown role: " + role);
            }
            if (newRole == Role.SUPERADMIN && !actorIsSuperadmin) {
                throw new BusinessException("Only a superadmin can grant the superadmin role");
            }
            target.setRole(newRole);
        }

        if (memberType != null && !memberType.isBlank()) {
            try {
                target.setMemberType(MemberType.valueOf(memberType.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Unknown member type: " + memberType);
            }
        }

        userRepository.save(target);
        return enrich(target);
    }

    @Transactional
    public UserDto setBasicTrainingFinished(Long userId, boolean finished) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setIsFinishedBasicTraining(finished);
        userRepository.save(user);
        return enrich(user);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public void saveRefreshToken(Long userId, String refreshToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setRefreshToken(refreshToken);
        userRepository.save(user);
    }
}
