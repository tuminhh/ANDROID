<?php
ini_set('display_errors', 0);
error_reporting(E_ALL);
header("Content-Type: application/json; charset=UTF-8");

$host = "db"; 
$db_user = "root";
$db_password = "rootpassword";
$db_name = "attendance_db";

if (!isset($_FILES['image']) || $_FILES['image']['error'] != UPLOAD_ERR_OK) {
    http_response_code(400);
    echo json_encode(["status" => "error", "message" => "Không tìm thấy file ảnh"]);
    exit;
}

$username = isset($_POST['username']) ? $_POST['username'] : 'unknown';

$uploadDir = '../uploads/'; 
$datasetDir = '../dataset/'; 

if (!is_dir($uploadDir)) mkdir($uploadDir, 0777, true);

$fileName = $username . '_' . time() . '.jpg';
$uploadFilePath = $uploadDir . $fileName;

if (move_uploaded_file($_FILES['image']['tmp_name'], $uploadFilePath)) {
    
    $ai_url = "http://ai_engine:5000/verify";

    $data = array(
        "uploaded_img" => "/app/uploads/" . $fileName, 
        "original_img" => "/app/dataset/" . $username . ".jpg"
    );

    $ch = curl_init($ai_url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_HTTPHEADER, array('Content-Type: application/json'));
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($data));
    curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 5);
    curl_setopt($ch, CURLOPT_TIMEOUT, 60);
    
    $ai_response = curl_exec($ch);
    $http_code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    if ($http_code == 200 && $ai_response) {
        $ai_result = json_decode($ai_response, true);
        
        if ($ai_result['status'] == 'success' && $ai_result['verified'] == true) {

            $conn = new mysqli($host, $db_user, $db_password, $db_name);
            if (!$conn->connect_error) {
                $username_db = $conn->real_escape_string($username);
                $path_db = $conn->real_escape_string("uploads/" . $fileName);
                $sql = "INSERT INTO attendance_records (username, image_path, status) VALUES ('$username_db', '$path_db', 'success')";
                $conn->query($sql);
                $conn->close();
            }

            echo json_encode([
                "status" => "success", 
                "message" => "Điểm danh thành công! (Sai số: " . round($ai_result['distance'], 2) . ")"
            ]);
        } else {
            echo json_encode(["status" => "error", "message" => "Khuôn mặt không khớp! Vui lòng thử lại."]);
        }
    } else {
        echo json_encode(["status" => "error", "message" => "Lỗi xử lý từ AI Engine."]);
    }

} else {
    http_response_code(500);
    echo json_encode(["status" => "error", "message" => "Lỗi lưu file vào hệ thống."]);
}
?>