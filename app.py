from flask import Flask, render_template_string, request

app = Flask(__name__)

HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <title>PhoneZone Automation Test Site</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background: #f4f4f4; }
        .section { background: white; padding: 20px; margin-bottom: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        h2 { color: #d70018; margin-top: 0; }
        input, button { display: block; margin: 10px 0; padding: 8px; width: 300px; }
        button { background: #d70018; color: white; border: none; cursor: pointer; }
        .msg { font-weight: bold; color: blue; }
        .error { color: red; }
    </style>
</head>
<body>
    <h1>PhoneZone - Hệ Thống Kiểm Thử Tự Động</h1>

    <!-- 1. ĐĂNG NHẬP -->
    <div class="section">
        <h2>Đăng Nhập</h2>
        <input type="text" id="login_user" placeholder="Username">
        <input type="password" id="login_pass" placeholder="Password">
        <button id="btnLogin" onclick="login()">Đăng nhập</button>
        <p id="login_msg" class="msg"></p>
    </div>

    <!-- 2. ĐĂNG KÝ -->
    <div class="section">
        <h2>Đăng Ký</h2>
        <input type="text" id="reg_email" placeholder="Email">
        <input type="password" id="reg_pass" placeholder="Mật khẩu">
        <input type="password" id="reg_confirm" placeholder="Xác nhận mật khẩu">
        <input type="text" id="reg_phone" placeholder="Số điện thoại">
        <button id="btnRegister" onclick="register()">Đăng ký</button>
        <p id="reg_msg" class="msg"></p>
    </div>

    <!-- 3. GIỎ HÀNG -->
    <div class="section">
        <h2>Giỏ Hàng</h2>
        <p>Sản phẩm: iPhone 15 Pro Max - Đơn giá: 20.000.000đ</p>
        <label>Số lượng: <input type="number" id="cart_qty" value="1" min="1" style="width:60px; display:inline;"></label>
        <button id="btnUpdateCart" onclick="updateCart()">Cập nhật giỏ hàng</button>
        <p>Tổng tiền: <span id="cart_total">20000000</span>đ</p>
    </div>

    <!-- 4. ĐẶT HÀNG -->
    <div class="section">
        <h2>Đặt Hàng</h2>
        <input type="text" id="order_name" placeholder="Họ và tên">
        <input type="text" id="order_phone" placeholder="Số điện thoại">
        <input type="text" id="order_address" placeholder="Địa chỉ">
        <button id="btnOrder" onclick="order()">Đặt hàng</button>
        <p id="order_msg" class="msg"></p>
    </div>

    <script>
        function login() {
            let u = document.getElementById('login_user').value.trim();
            let p = document.getElementById('login_pass').value.trim();
            let msg = document.getElementById('login_msg');
            if(!u && !p) msg.innerText = "Vui lòng nhập username và password";
            else if(!u) msg.innerText = "Vui lòng nhập username";
            else if(!p) msg.innerText = "Vui lòng nhập password";
            else if(u === "user1" && p === "Password123") msg.innerText = "Đăng nhập thành công";
            else if(u === "user1") msg.innerText = "Sai password";
            else msg.innerText = "Username không tồn tại";
        }

        function register() {
            let e = document.getElementById('reg_email').value.trim();
            let p = document.getElementById('reg_pass').value.trim();
            let c = document.getElementById('reg_confirm').value.trim();
            let ph = document.getElementById('reg_phone').value.trim();
            let msg = document.getElementById('reg_msg');

            if(!e || !p || !ph) msg.innerText = "Vui lòng điền đầy đủ trường bắt buộc";
            else if(p !== c) msg.innerText = "Mật khẩu xác nhận không khớp";
            else if(!e.includes("@") || isNaN(ph)) msg.innerText = "Email hoặc SĐT không hợp lệ";
            else if(p.length < 6) msg.innerText = "Mật khẩu quá ngắn";
            else if(e === "exist@gmail.com") msg.innerText = "Đăng ký thành công"; // MÔ PHỎNG LỖI: Cho phép trùng email
            else msg.innerText = "Đăng ký thành công";
        }

        function updateCart() {
            let q = parseInt(document.getElementById('cart_qty').value);
            // MÔ PHỎNG LỖI: Tính sai tổng tiền (cộng dư 50.000đ)
            document.getElementById('cart_total').innerText = (q * 20000000 + 50000);
        }

        function order() {
            let n = document.getElementById('order_name').value.trim();
            let ph = document.getElementById('order_phone').value.trim();
            let a = document.getElementById('order_address').value.trim();
            let msg = document.getElementById('order_msg');

            if(!n || !ph || !a) msg.innerText = "Thiếu thông tin bắt buộc";
            else if(isNaN(ph) || ph.length < 9) msg.innerText = "Số điện thoại không hợp lệ";
            else msg.innerText = "Đặt hàng thất bại: Lỗi kết nối máy chủ"; // MÔ PHỎNG LỖI: Luôn thất bại
        }
    </script>
</body>
</html>
"""

@app.route('/')
def index():
    return render_template_string(HTML_TEMPLATE)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080, debug=True)