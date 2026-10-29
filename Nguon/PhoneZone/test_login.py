from selenium import webdriver
from selenium.webdriver.common.by import By

# Mở Chrome
driver = webdriver.Chrome()

# Mở trang đăng nhập PhoneZone
driver.get("http://localhost:8080/login.html")

# Nhập tài khoản
driver.find_element(By.ID, "username").send_keys("user01")

# Nhập mật khẩu
driver.find_element(By.ID, "pw").send_keys("123456")

# Giữ Chrome để quan sát
input("Nhấn Enter để kết thúc kiểm thử...")

# Đóng Chrome
driver.quit()