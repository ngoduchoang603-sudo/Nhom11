from selenium import webdriver

driver = webdriver.Chrome()
driver.get("http://localhost:8080")

print("Tiêu đề:", driver.title)

input("Nhấn Enter để đóng trình duyệt...")

driver.quit()