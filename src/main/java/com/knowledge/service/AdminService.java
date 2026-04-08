package com.knowledge.service;

import com.knowledge.dto.AdminOverviewDto;
import com.knowledge.dto.AdminUserCreateRequest;
import com.knowledge.dto.AdminUserUpdateRequest;
import com.knowledge.dto.PageDto;
import com.knowledge.dto.UserInfo;
import com.knowledge.entity.User;
import com.knowledge.exception.BusinessException;
import com.knowledge.repository.NoteRepository;
import com.knowledge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final PasswordEncoder passwordEncoder;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public AdminOverviewDto overview() {
        List<UserInfo> recentUsers = userRepository.findTop10ByOrderByCreatedAtDesc().stream()
            .map(this::toUserInfo)
            .toList();

        return new AdminOverviewDto(
            userRepository.count(),
            userRepository.countByRole("ADMIN"),
            noteRepository.count(),
            recentUsers
        );
    }

    public PageDto<UserInfo> listUsers(int page, int size, String keyword, String role) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        String kw = keyword == null ? null : keyword.trim();
        String rr = role == null ? null : role.trim();
        if (rr != null && !rr.isEmpty() && !"USER".equals(rr) && !"ADMIN".equals(rr)) {
            throw BusinessException.badRequest("role 参数只能是 USER 或 ADMIN");
        }

        Page<User> result = userRepository.searchUsers(
            kw,
            rr,
            PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "id"))
        );

        List<UserInfo> content = result.getContent().stream().map(this::toUserInfo).toList();
        return new PageDto<>(
            content,
            result.getTotalElements(),
            result.getTotalPages(),
            result.getNumber(),
            result.getSize()
        );
    }

    @Transactional
    public UserInfo createUser(AdminUserCreateRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw BusinessException.badRequest("邮箱已被使用");
        }
        User user = new User();
        user.setUsername(req.getUsername().trim());
        user.setEmail(req.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole(req.getRole().trim());
        userRepository.save(user);
        return toUserInfo(user);
    }

    @Transactional
    public UserInfo updateUser(Long id, AdminUserUpdateRequest req) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        String newRole = req.getRole().trim();
        assertNotLastAdminDemotion(user, newRole);

        String email = req.getEmail().trim().toLowerCase();
        userRepository.findByEmail(email).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw BusinessException.badRequest("邮箱已被其他用户使用");
            }
        });

        user.setUsername(req.getUsername().trim());
        user.setEmail(email);
        user.setRole(newRole);

        String pwd = req.getPassword();
        if (pwd != null && !pwd.isBlank()) {
            if (pwd.length() < 6 || pwd.length() > 100) {
                throw BusinessException.badRequest("密码长度 6-100 个字符");
            }
            user.setPassword(passwordEncoder.encode(pwd));
        }

        userRepository.save(user);
        return toUserInfo(user);
    }

    @Transactional
    public void deleteUser(Long id, Long currentUserId) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        if (user.getId().equals(currentUserId)) {
            throw BusinessException.badRequest("不能删除当前登录账号");
        }

        if (normalizeRole(user).equals("ADMIN") && userRepository.countByRole("ADMIN") <= 1) {
            throw BusinessException.badRequest("至少需要保留一名管理员");
        }

        if (noteRepository.countByUserId(user.getId()) > 0) {
            throw BusinessException.badRequest("该用户仍有关联笔记，无法删除");
        }

        userRepository.delete(user);
    }

    private void assertNotLastAdminDemotion(User user, String newRole) {
        if (!normalizeRole(user).equals("ADMIN")) {
            return;
        }
        if (newRole.equals("ADMIN")) {
            return;
        }
        if (userRepository.countByRole("ADMIN") <= 1) {
            throw BusinessException.badRequest("至少需要保留一名管理员");
        }
    }

    private String normalizeRole(User user) {
        String r = user.getRole();
        return (r == null || r.isBlank()) ? "USER" : r;
    }

    private UserInfo toUserInfo(User user) {
        String role = normalizeRole(user);
        return new UserInfo(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            role,
            user.getCreatedAt().format(DATE_FMT)
        );
    }
}
