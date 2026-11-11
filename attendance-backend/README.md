# Backend điểm danh thông minh (Spring Boot 3, Java 17)

API REST cho app Android điểm danh bằng khuôn mặt: đăng nhập JWT, phân quyền sinh viên/giảng viên,
lưu vector đặc trưng khuôn mặt, mở/đóng buổi điểm danh, so khớp, giới hạn lần thử, kiểm tra vị trí, xuất CSV.

## Chạy thử (dev, dùng H2 lưu file trong `./data`)
Cần JDK 17+ và Maven 3.9+.

```bash
mvn test               # chạy toàn bộ kiểm thử
mvn spring-boot:run    # chạy ở http://localhost:8080
```
Ở chế độ dev, mã mời đăng ký giảng viên mặc định là `GIANGVIEN-DEV`. Xem `requests.http` để thử nhanh.

## Chạy thật bằng Docker (PostgreSQL)
```bash
cp .env.example .env   # điền DB_PASSWORD, JWT_SECRET (>= 32 ký tự), TEACHER_INVITE_CODE
docker compose up --build
```
Bắt buộc đặt API sau reverse proxy có **HTTPS** (Nginx/Caddy). Không mở cổng 8080 ra Internet dạng HTTP thường.

## API

Mọi lỗi đều trả JSON `{ "code": "...", "message": "...", "fields": {...} }`. Token gửi qua `Authorization: Bearer <token>`.

| Phương thức | Đường dẫn | Vai trò | Mô tả |
|---|---|---|---|
| POST | `/api/auth/register` | công khai | Đăng ký. Giảng viên cần `inviteCode` đúng. Trả `token` và thông tin người dùng |
| POST | `/api/auth/login` | công khai | Đăng nhập. Sai 5 lần sẽ bị khóa tạm 10 phút |
| GET | `/api/me` | đã đăng nhập | Thông tin hiện tại, có `faceEnrolled` |
| PUT | `/api/face` | sinh viên | Đăng ký khuôn mặt: `{ "embedding": [192 số], "password": "chỉ cần khi đăng ký lại" }` |
| GET | `/api/face/status` | sinh viên | `{ "enrolled": true }` |
| POST | `/api/classes` | giảng viên | Tạo lớp học phần: `{ "code", "name", "semester" }` (mã lớp duy nhất) |
| GET | `/api/classes/mine` | giảng viên | Các lớp của mình, kèm `studentCount` |
| GET | `/api/classes/enrolled` | sinh viên | Các lớp mình đang học |
| GET | `/api/classes/{id}` | giảng viên của lớp | Thông tin lớp và danh sách sinh viên |
| POST | `/api/classes/{id}/students` | giảng viên của lớp | Thêm hàng loạt: `{ "studentIds": [...] }` → `added`, `alreadyEnrolled`, `notFound` (sinh viên phải đã có tài khoản) |
| DELETE | `/api/classes/{id}/students/{studentId}` | giảng viên của lớp | Bỏ sinh viên khỏi lớp |
| GET | `/api/classes/{id}/report` | giảng viên của lớp | Báo cáo chuyên cần: số buổi có mặt, tổng buổi, tỷ lệ % từng sinh viên |
| GET | `/api/classes/{id}/export.xlsx` | giảng viên của lớp | Tải bảng điểm danh cả lớp dạng Excel (.xlsx), mỗi cột một buổi |
| GET | `/api/classes/{id}/export` | giảng viên của lớp | Tải báo cáo CSV |
| POST | `/api/sessions` | giảng viên | Mở buổi cho một lớp của mình: `{ "classId", "title", "durationMinutes", "latitude", "longitude", "radiusMeters" }` (vị trí tùy chọn) |
| GET | `/api/sessions/active` | đã đăng nhập | Các buổi đang mở. Sinh viên chỉ thấy buổi của lớp mình đã được thêm vào (không kèm tọa độ) |
| GET | `/api/sessions/mine` | giảng viên | Các buổi của mình |
| POST | `/api/sessions/{id}/close` | giảng viên | Đóng buổi |
| GET | `/api/sessions/{id}/export.xlsx` | giảng viên | Tải Excel (.xlsx): có mặt tô xanh, vắng tô đỏ, kèm tiêu đề lớp/buổi và tổng kết |
| GET | `/api/sessions/{id}/records` | giảng viên | Có mặt (`records`), vắng (`absent`) và `rosterSize` |
| GET | `/api/sessions/{id}/export` | giảng viên | Tải CSV (UTF-8 BOM, mở được bằng Excel), có cả sinh viên vắng |
| POST | `/api/sessions/{id}/records` | giảng viên | Điểm danh thủ công: `{ "studentId" }` (sinh viên phải thuộc lớp) |
| DELETE | `/api/sessions/{id}/records/{studentId}` | giảng viên | Xóa một bản ghi |
| POST | `/api/attendance` | sinh viên | Điểm danh: `{ "sessionId", "embedding", "latitude", "longitude" }` |
| GET | `/api/attendance/history` | sinh viên | Lịch sử điểm danh của mình |

Mã lỗi nghiệp vụ thường gặp khi điểm danh: `SESSION_CLOSED` (410), `ALREADY_MARKED` (409), `NOT_ENROLLED` (403, sinh viên không thuộc lớp của buổi), `FACE_NOT_ENROLLED` (400),
`LOCATION_REQUIRED` (400), `OUT_OF_RANGE` (403), `ATTEMPTS_EXHAUSTED` (429).
Sai khuôn mặt trả HTTP 200 với `matched: false` và `attemptsLeft` (không trả độ tương đồng để tránh bị dò).

## Nối với app Android
- Máy ảo gọi máy chủ trên máy tính qua `http://10.0.2.2:8080`. Với HTTP thường cần bật `android:usesCleartextTraffic="true"` **chỉ khi dev**.
- App tiếp tục tự trích vector 192 chiều bằng MobileFaceNet, rồi gửi `embedding` lên thay vì lưu và so khớp cục bộ.
  `FaceActivity` chỉ cần đổi hai chỗ: khi đăng ký gọi `PUT /api/face`, khi điểm danh gọi `POST /api/attendance`.
- Mật khẩu tối thiểu giờ là 8 ký tự (app đang kiểm tra 6) và mã người dùng không phân biệt hoa thường (lưu dạng chữ hoa).
- Nên thêm Retrofit + OkHttp interceptor gắn token, lưu token bằng EncryptedSharedPreferences.

## Cấu hình chính (`application.yml`)
| Khóa | Mặc định | Ý nghĩa |
|---|---|---|
| `app.face.threshold` | 0.70 | Ngưỡng cosine chấp nhận. Hãy đo lại trên dữ liệu thật của nhóm |
| `app.face.dimension` | 192 | Số chiều vector (MobileFaceNet) |
| `app.face.max-failed-attempts` | 5 | Số lần sai tối đa của mỗi sinh viên trong một buổi |
| `app.session.default-minutes` | 15 | Thời lượng mở buổi mặc định |
| `app.teacher-invite-code` | (trống) | Mã mời giảng viên; để trống = tắt đăng ký giảng viên |

## Cấu trúc mã
`web/` controller và DTO · `service/` nghiệp vụ · `domain/` + `repo/` JPA · `security/` JWT · `util/` toán vector, GPS, CSV ·
`src/test` kiểm thử tích hợp toàn bộ luồng (MockMvc + H2).
