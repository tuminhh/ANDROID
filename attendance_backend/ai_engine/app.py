from flask import Flask, request, jsonify
from deepface import DeepFace
import os

app = Flask(__name__)

@app.route('/verify', methods=['POST'])
def verify_face():
    data = request.json
    uploaded_img = data.get('uploaded_img') # Đường dẫn ảnh chụp từ app
    original_img = data.get('original_img') # Đường dẫn ảnh gốc của sinh viên

    if not os.path.exists(uploaded_img) or not os.path.exists(original_img):
        return jsonify({"status": "error", "message": "Không tìm thấy file ảnh trên Server"}), 400

    try:
        # Sử dụng DeepFace để so sánh 2 khuôn mặt
        result = DeepFace.verify(
            img1_path=uploaded_img, 
            img2_path=original_img, 
            model_name="VGG-Face", 
            enforce_detection=False # Tránh crash nếu ảnh quá tối không thấy rõ mặt
        )
        
        return jsonify({
            "status": "success",
            "verified": bool(result["verified"]),
            "distance": float(result["distance"])
        })
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

if __name__ == '__main__':
    # Chạy server AI ở port 5000
    app.run(host='0.0.0.0', port=5000)