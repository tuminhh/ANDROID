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


## Cấu trúc
- `net/`: Retrofit (`ApiService`, `ApiClient`, `ApiCallback`), đối tượng JSON (`Models`), `TokenStore` (token mã hóa bằng Keystore)
- `face/`: `FaceEmbedder` (TFLite MobileFaceNet), `Embeddings`
- `ui/`: Login, Main, FaceActivity (CameraX + ML Kit + chớp mắt + GPS), RecordListActivity
- `util/`: `LocationHelper` (vị trí, từ chối vị trí giả lập), `TimeUtil`
- `assets/mobilefacenet.tflite`: MobileFaceNet 112x112 → vector 192 chiều, từ
  github.com/syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing (giấy phép MIT)


