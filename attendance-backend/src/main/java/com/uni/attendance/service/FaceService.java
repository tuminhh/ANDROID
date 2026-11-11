package com.uni.attendance.service;

import com.uni.attendance.config.AppProperties;
import com.uni.attendance.domain.FaceTemplate;
import com.uni.attendance.domain.User;
import com.uni.attendance.repo.FaceTemplateRepository;
import com.uni.attendance.repo.UserRepository;
import com.uni.attendance.util.Embeddings;
import com.uni.attendance.web.ApiException;
import com.uni.attendance.web.Dtos.FaceEnrollRequest;
import com.uni.attendance.web.Dtos.FaceStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FaceService {
    private final FaceTemplateRepository faces;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public FaceService(FaceTemplateRepository faces, UserRepository users,
                       PasswordEncoder encoder, AppProperties props) {
        this.faces = faces;
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    /**
     * Lưu mẫu khuôn mặt. Lần đầu không cần mật khẩu; đăng ký lại bắt buộc nhập mật khẩu,
     * để người nhặt được token/điện thoại không thể thay mặt người khác bằng khuôn mặt của mình.
     */
    @Transactional
    public void enroll(String userId, FaceEnrollRequest r) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Tài khoản không tồn tại"));
        FaceTemplate existing = faces.findById(userId).orElse(null);
        if (existing != null) {
            boolean ok = r.password() != null && encoder.matches(r.password(), user.getPasswordHash());
            if (!ok) {
                throw new ApiException(HttpStatus.FORBIDDEN, "PASSWORD_REQUIRED",
                        "Cần nhập đúng mật khẩu để đăng ký lại khuôn mặt");
            }
        }
        String serialized = Embeddings.serialize(prepare(r.embedding()));
        if (existing == null) {
            faces.save(new FaceTemplate(userId, serialized));
        } else {
            existing.replace(serialized);
        }
    }

    @Transactional(readOnly = true)
    public FaceStatus status(String userId) {
        return new FaceStatus(faces.existsById(userId));
    }

    /** Kiểm tra số chiều và giá trị rồi chuẩn hóa vector gửi lên. */
    public float[] prepare(float[] raw) {
        int dim = props.face().dimension();
        if (raw == null || raw.length != dim) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMBEDDING",
                    "Vector khuôn mặt phải có đúng " + dim + " chiều");
        }
        try {
            return Embeddings.normalized(raw);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMBEDDING", "Vector khuôn mặt không hợp lệ");
        }
    }
}
