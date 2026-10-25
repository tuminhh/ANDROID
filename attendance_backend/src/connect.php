<?php
// Thiết lập header trả về JSON
header("Content-Type: application/json; charset=UTF-8");

$host = "db"; // Tên service database trong file docker-compose.yml
$user = "root";
$password = "rootpassword";
$db_name = "attendance_db";

// Thử kết nối
$conn = @mysqli_connect($host, $user, $password, $db_name);

if (!$conn) {
    // Trả về lỗi nếu không kết nối được
    http_response_code(500);
    echo json_encode([
        "status" => "error", 
        "message" => "Không thể kết nối CSDL: " . mysqli_connect_error()
    ]);
} else {
    // Trả về thành công
    http_response_code(200);
    echo json_encode([
        "status" => "success", 
        "message" => "Kết nối thành công từ Android đến Docker (PHP + MySQL)!"
    ]);
    mysqli_close($conn);
}
?>