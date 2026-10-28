package com.stayhub.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.user.dto.UpdateProfileRequest;
import com.stayhub.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        return userMapper.toResponse(findEntityByEmail(email));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userMapper.toResponse(findEntityById(id));
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = findEntityById(userId);
        
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException("ERR_INVALID_PASSWORD", "Incorrect old password.");
        }
        
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findEntityById(userId);
        user.setFullName(request.getFullName().trim());
        String phone = request.getPhone();
        user.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        return userMapper.toResponse(userRepository.save(user));
    }


    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }   

    @Override
    @Transactional
    public void lockUser(Long userId) {
        User user = findEntityById(userId);
        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException("ERR_LOCK_ADMIN", "Cannot lock an admin account.");
        }
        user.setStatus(UserStatus.LOCKED);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void unlockUser(Long userId) {
        User user = findEntityById(userId);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserResponse changeRole(Long userId, UserRole role) {
        User user = findEntityById(userId);
        if (user.getRole() == UserRole.ADMIN && role != UserRole.ADMIN
                && userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BusinessException("ERR_LAST_ADMIN", "Cannot remove the last active admin account.");
        }
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userMapper.toResponse(userRepository.save(user));
    }

    private User findEntityByEmail(String email) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normalizedEmail));
    }

    private User findEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
    }
}
