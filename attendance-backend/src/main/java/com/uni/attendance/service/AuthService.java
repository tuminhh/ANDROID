package com.uni.attendance.service;

import com.uni.attendance.config.AppProperties;
import com.uni.attendance.domain.Role;
import com.uni.attendance.domain.User;
import com.uni.attendance.repo.FaceTemplateRepository;
import com.uni.attendance.repo.UserRepository;
import com.uni.attendance.security.JwtService;
import com.uni.attendance.web.ApiException;
import com.uni.attendance.web.Dtos.AuthResponse;
import com.uni.attendance.web.Dtos.LoginRequest;
import com.uni.attendance.web.Dtos.RegisterRequest;
import com.uni.attendance.web.Dtos.UserDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {
    /** BCrypt chỉ xử lý tối đa 72 byte đầu của mật khẩu. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final FaceTemplateRepository faces;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AppProperties props;
    private final LoginThrottle throttle;
    /** Băm giả để thời gian phản hồi không lộ việc mã người dùng có tồn tại hay không. */
    private final String dummyHash;

    public AuthService(UserRepository users, FaceTemplateRepository faces, PasswordEncoder encoder,
                       JwtService jwt, AppProperties props, LoginThrottle throttle) {
        this.users = users;
        this.faces = faces;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
        this.throttle = throttle;
        this.dummyHash = encoder.encode("mat-khau-gia-de-can-bang-thoi-gian");
    }

    /** Mã người dùng không phân biệt hoa thường: lưu dạng chữ hoa. */
    public static String normalizeId(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
    }

    @Transactional
    public AuthResponse register(RegisterRequest r) {
        if (r.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG", "Mật khẩu quá dài");
        }
        if (r.role() == Role.TEACHER) {
            checkInviteCode(r.inviteCode());
        }
        String id = normalizeId(r.userId());
        if (users.existsById(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_EXISTS", "Mã người dùng đã tồn tại");
        }
        User user = users.save(new User(id, r.name().trim(), encoder.encode(r.password()), r.role()));
        return new AuthResponse(jwt.issue(user.getUserId(), user.getRole()), toDto(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest r) {
        String id = normalizeId(r.userId());
        if (throttle.isLocked(id)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Đăng nhập sai quá nhiều lần, vui lòng thử lại sau ít phút");
        }
        Optional<User> found = users.findById(id);
        boolean passwordOk = r.password().getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES
                && encoder.matches(r.password(), found.map(User::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk) {
            throttle.recordFailure(id);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Sai mã người dùng hoặc mật khẩu");
        }
        throttle.recordSuccess(id);
        User user = found.get();
        return new AuthResponse(jwt.issue(user.getUserId(), user.getRole()), toDto(user));
    }

    @Transactional(readOnly = true)
    public UserDto me(String userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Tài khoản không tồn tại"));
        return toDto(user);
    }

    private void checkInviteCode(String given) {
        String expected = props.teacherInviteCode();
        if (expected == null || expected.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TEACHER_REGISTRATION_DISABLED",
                    "Hệ thống không mở đăng ký giảng viên");
        }
        byte[] a = (given == null ? "" : given).getBytes(StandardCharsets.UTF_8);
        byte[] b = expected.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(a, b)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_INVITE_CODE", "Mã mời giảng viên không đúng");
        }
    }

    private UserDto toDto(User user) {
        return new UserDto(user.getUserId(), user.getName(), user.getRole(), faces.existsById(user.getUserId()));
    }
}
