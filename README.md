# Điểm danh thông minh bằng nhận diện khuôn mặt (Android, Java)

App Android nối với backend Spring Boot (thư mục `attendance-backend` đi kèm). App chỉ **trích vector khuôn mặt**
bằng MobileFaceNet rồi gửi lên máy chủ; việc so khớp, giới hạn lần thử, kiểm tra vị trí và lưu điểm danh do backend làm.

## Chạy cùng backend
1. Chạy backend (`mvn spring-boot:run` trong `attendance-backend`, mặc định cổng 8080, mã mời giảng viên dev là `GIANGVIEN-DEV`).
2. Mở thư mục `SmartAttendance` bằng Android Studio (Koala trở lên, JDK 17), chờ Gradle sync, bấm **Run**.
3. **Máy ảo**: dùng sẵn địa chỉ `http://10.0.2.2:8080/` (chính là máy tính của bạn).
   **Điện thoại thật cùng Wi-Fi**:
   - build với `-PbaseUrl=http://192.168.0.104:8080/` (đổi IP cho đúng máy tính của bạn), hoặc đặt `baseUrl=...` trong `gradle.properties`;
     bản debug đã cho phép HTTP thường tới mọi địa chỉ nên không cần sửa thêm cấu hình mạng;
   - máy tính và điện thoại phải cùng Wi-Fi (một số Wi-Fi công cộng/trường chặn thiết bị nói chuyện với nhau);
   - mở cổng 8080 trong tường lửa của máy tính (Windows: cho phép Java qua Windows Defender Firewall);
   - thử trước trên trình duyệt điện thoại: mở `http://192.168.0.104:8080/api/face/status`, thấy trả về JSON lỗi 401 nghĩa là đã tới được backend.
4. **Bản release** bắt buộc HTTPS: build với `-PreleaseBaseUrl=https://ten-mien-cua-ban/`.

## Cách dùng thử
- **Giảng viên**: Đăng ký (chọn "Giảng viên", nhập mã mời) → *Mở buổi điểm danh* (đặt tên, thời lượng, tùy chọn giới hạn bán kính 100 m quanh vị trí của bạn).
  Sau đó *Xem danh sách có mặt*: nhấn giữ một dòng để xóa, *Thêm thủ công* để đánh dấu có mặt, *Xuất CSV* để chia sẻ file.
- **Sinh viên** (tài khoản khác): Đăng ký → *Đăng ký khuôn mặt* (đồng ý sử dụng dữ liệu, nhìn thẳng ~3 giây) → *Điểm danh* (chọn buổi, nhìn thẳng và chớp mắt một lần).
- Đăng ký lại khuôn mặt phải nhập mật khẩu. Sai quá 5 lần trong một buổi thì bị khóa, giảng viên điểm danh thủ công cho sinh viên đó.

## Cấu trúc
- `net/`: Retrofit (`ApiService`, `ApiClient`, `ApiCallback`), đối tượng JSON (`Models`), `TokenStore` (token mã hóa bằng Keystore)
- `face/`: `FaceEmbedder` (TFLite MobileFaceNet), `Embeddings`
- `ui/`: Login, Main, FaceActivity (CameraX + ML Kit + chớp mắt + GPS), RecordListActivity
- `util/`: `LocationHelper` (vị trí, từ chối vị trí giả lập), `TimeUtil`
- `assets/mobilefacenet.tflite`: MobileFaceNet 112x112 → vector 192 chiều, từ
  github.com/syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing (giấy phép MIT)

## Thay đổi so với bản trước
- **Phân tách và tối ưu hóa giao diện Giảng viên vs Sinh viên**:
  - Giao diện Giảng viên chuyên biệt: Quản lý lớp học phần, Nhập lớp bằng JSON, Mở điểm danh, Xem danh sách có mặt, Lịch giảng dạy và Tóm tắt lịch dạy. Đã loại bỏ các nút dư thừa không thuộc vai trò giáo viên (nhận diện khuôn mặt sinh viên, đăng ký mẫu mặt cá nhân, nút đóng buổi rời rạc). Thẻ buổi điểm danh đang hoạt động chỉ hiện khi có phiên đang mở.
  - Giao diện Sinh viên: Thời khóa biểu, Điểm danh khuôn mặt, Đăng ký khuôn mặt, Lớp của bạn, Lịch sử điểm danh và Tóm tắt TKB.
- **Nhập lớp học phần bằng JSON (Dành riêng cho Giảng viên)**: Giảng viên có thể dán mảng JSON chứa mã lớp, tên môn học, học kỳ, thứ trong tuần, ca học, phòng học và danh sách mã sinh viên. Hệ thống tự động tạo lớp và ghi danh sinh viên.
- **Thời khóa biểu Sinh viên tự động cập nhật theo Giảng viên**: Học sinh không cần và không thể tự thêm TKB; lịch học của học sinh được tự động đồng bộ theo các lớp học phần mà giảng viên đã tạo và phân công.
- **Tóm tắt thời khóa biểu**: Thống kê chi tiết số lớp, số môn, phòng học và phân bố ca học trong tuần. Đã bỏ phần thông báo gần đây theo yêu cầu.
- **Xuất Excel (.xlsx)** cho từng buổi (có mặt tô xanh, vắng tô đỏ) và cho cả lớp; vẫn còn CSV. Chọn định dạng khi bấm *Xuất file*. Cần backend bản mới.
- **Thử thách sống khi điểm danh**: chớp mắt, rồi quay đầu sang hướng trái/phải chọn ngẫu nhiên mỗi lần, rồi nhìn thẳng mới lấy mẫu
  (hết 15 giây thì làm lại với hướng mới). Video quay sẵn khó đáp ứng được hướng ngẫu nhiên, nhưng chưa chặn được deepfake theo thời gian thực.
  Dấu góc quay đầu của ML Kit chưa kiểm chứng trên máy thật: nếu gợi ý "PHẢI" mà phải quay sang trái mới qua, đổi `RIGHT_TURN_SIGN` trong `FaceActivity` thành -1.
- **Lớp học phần và danh sách sinh viên**: giảng viên tạo lớp, dán danh sách mã sinh viên (tối đa 500 mỗi lần), nhấn giữ để bỏ sinh viên,
  xem báo cáo chuyên cần và xuất CSV. Buổi điểm danh phải thuộc một lớp; sinh viên chỉ thấy và điểm danh được buổi của lớp mình.
  Màn hình xem buổi hiển thị cả sinh viên vắng và cho đánh dấu có mặt thủ công. Cần backend bản mới tương ứng.
- **Sửa lỗi nghiêm trọng**: mô hình nhận một lô **2 ảnh** (`[2,112,112,3]` → `[2,192]`), còn bản trước chỉ cấp bộ nhớ cho 1 ảnh nên sẽ lỗi
  ngay khi trích vector. `FaceEmbedder` giờ đọc kích thước lô từ mô hình và nhân đôi ảnh cho đủ lô.
- Bỏ Room và lưu cục bộ; dữ liệu nằm ở backend. Mật khẩu tối thiểu 8 ký tự, mã người dùng không phân biệt hoa thường.
- Thêm: màn hình đồng ý sinh trắc học, token mã hóa, tự quay về đăng nhập khi token hết hạn, GPS, xuất CSV.

## Hạn chế cần biết
- **Ngưỡng nhận diện (`app.face.threshold` ở backend, mặc định 0.70) chưa được hiệu chỉnh.** App cắt mặt theo khung của ML Kit,
  không căn chỉnh như mô hình mong đợi nên độ khớp của cùng một người có thể thấp hơn kỳ vọng. Hãy thử với nhiều người,
  nhiều điều kiện ánh sáng và đọc phần trăm khớp hiển thị trong lịch sử, rồi chỉnh ngưỡng.
- Chống gian lận là thử thách chớp mắt + quay đầu ngẫu nhiên (xử lý trong app) và kiểm tra vị trí; chưa chặn được deepfake thời gian thực hoặc app bị sửa.
- Mã chưa được biên dịch hay chạy thử trong môi trường tạo ra nó (không có Android SDK). Lần Sync/Run đầu có thể báo lỗi nhỏ.
