package com.knowledge.service;

import com.knowledge.dto.AuthResponse;
import com.knowledge.dto.LoginRequest;
import com.knowledge.dto.RegisterRequest;
import com.knowledge.dto.UserInfo;
import com.knowledge.entity.User;
import com.knowledge.exception.BusinessException;
import com.knowledge.repository.UserRepository;
import com.knowledge.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw BusinessException.badRequest("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("USER");
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId());
        return new AuthResponse(token, toUserInfo(user));
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
            .orElseThrow(() -> BusinessException.unauthorized("邮箱或密码错误"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw BusinessException.unauthorized("邮箱或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId());
        return new AuthResponse(token, toUserInfo(user));
    }

    public UserInfo getMe(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        return toUserInfo(user);
    }

    private UserInfo toUserInfo(User user) {
        String role = (user.getRole() == null || user.getRole().isBlank()) ? "USER" : user.getRole();
        return new UserInfo(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            role,
            user.getCreatedAt().format(DATE_FMT)
        );
    }
}
