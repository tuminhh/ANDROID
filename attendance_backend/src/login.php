<?php
header("Content-Type: application/json; charset=UTF-8");

$host = "db"; 
$user = "root";
$password = "rootpassword";
$db_name = "attendance_db";

$conn = @mysqli_connect($host, $user, $password, $db_name);

if (!$conn) {
    http_response_code(500);
    die(json_encode(["status" => "error", "message" => "Lỗi kết nối CSDL"]));
}

// Lấy dữ liệu từ Android gửi lên (POST method)
$username = isset($_POST['username']) ? $_POST['username'] : '';
$pass = isset($_POST['password']) ? $_POST['password'] : '';

if (empty($username) || empty($pass)) {
    echo json_encode(["status" => "error", "message" => "Vui lòng nhập đầy đủ thông tin"]);
    exit;
}

// Chống SQL Injection cơ bản
$username = mysqli_real_escape_string($conn, $username);
$pass = mysqli_real_escape_string($conn, $pass);

// Truy vấn kiểm tra user
$sql = "SELECT id, full_name, role FROM users WHERE username = '$username' AND password = '$pass'";
$result = mysqli_query($conn, $sql);

if (mysqli_num_rows($result) > 0) {
    $row = mysqli_fetch_assoc($result);
    echo json_encode([
        "status" => "success", 
        "message" => "Đăng nhập thành công",
        "data" => $row
    ]);
} else {
    echo json_encode(["status" => "error", "message" => "Sai tài khoản hoặc mật khẩu"]);
}

mysqli_close($conn);
?>