# Website Bán Điện Thoại PhoneZone (Dịch vụ Nhà Bán Lẻ PhoneZone)

Dự án thực hiện môn Kiểm thử phần mềm (Software Testing)

👉 **Giới thiệu tài liệu Đề tài**
- **Tên dự án:** Website bán điện thoại PhoneZone (PhoneZone E-Commerce Service)
- **Repository:** https://github.com/ngoduchoang603-sudo/Nhom11
- **Mục tiêu:** Xây dựng hệ thống website bán điện thoại PhoneZone và phát triển kịch bản kiểm thử tự động giao diện người dùng (UI Automation Testing) toàn diện bằng Selenium WebDriver.

---

🛠️ **Công nghệ sử dụng (Tech Stack)**
- **Ngôn ngữ & Nền tảng hệ thống:** Java 21, Spring Boot 3.5.6 (hoặc Python 3.x cho Selenium Automation)
- **Cơ sở dữ liệu:** MySQL 8.0 (kết nối qua Spring Data JPA / Hibernate)
- **Công cụ tìm kiếm:** Elasticsearch 8.x (tìm kiếm nâng cao danh mục, thương hiệu & điện thoại)
- **Bảo mật & Phân quyền:** Spring Security 6, OAuth2 Resource Server, Nimbus Jose JWT
- **Hỗ trợ thư viện:** Lombok, MapStruct (DTO Mapping), Hibernate Validator
- **Công cụ Kiểm thử:**
  - **Selenium WebDriver:** Khung kiểm thử tự động giao diện End-to-End (Python / Java + NUnit / PyTest / JUnit)
  - **Trình duyệt & Driver:** Google Chrome & ChromeDriver

---

📂 **Cấu trúc Dự án**

```text
├── automation-tests/              # Kịch bản kiểm thử tự động UI bằng Selenium
│   ├── drivers/                   # ChromeDriver / GeckoDriver
│   ├── tests/                     # Các file kịch bản kiểm thử (Auth, Cart, Order, Search, Profile)
│   ├── pages/                     # Mẫu thiết kế Page Object Model (POM)
│   └── requirements.txt / pom.xml # Thư viện phụ thuộc cho Selenium
├── src/
│   ├── main/
│   │   ├── java/com/phonezone/
│   │   │   ├── configuration/     # Cấu hình Security, JWT, Khởi tạo dữ liệu mẫu
│   │   │   ├── controller/        # REST Controllers (API Endpoints)
│   │   │   ├── dto/               # Data Transfer Objects (Request/Response)
│   │   │   ├── entity/            # Các thực thể dữ liệu JPA
│   │   │   ├── enums/             # Enums trạng thái, vai trò người dùng
│   │   │   ├── exception/         # Xử lý ngoại lệ toàn cục (Global Exception Handling)
│   │   │   ├── mapper/            # MapStruct mappers
│   │   │   ├── repositories/      # JPA Repositories
│   │   │   ├── search/            # Elasticsearch Document & Repositories
│   │   │   └── services/          # Business logic services
│   │   └── resources/
│   │       ├── application.yaml   # Cấu hình kết nối DB, Port, JWT
│   │       └── elasticsearch-analyzer.json
│   └── test/                      # Unit Tests
├── mvnw / mvnw.cmd                # Maven wrapper script
├── pom.xml                        # File cấu hình phụ thuộc Maven
└── README.md                      # Tài liệu dự án


Yêu cầu môi trường
JDK: Java Development Kit 21 trở lên
MySQL: Phiên bản 8.0 trở lên
Python / Java: Để thực thi kịch bản kiểm thử Selenium
Trình duyệt & Driver: Google Chrome + ChromeDriver tương thích

Cấu hình Cơ sở dữ liệu
CREATE DATABASE IF NOT EXISTS phonezone CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

Cấu hình thông tin tài khoản MySQL trong file src/main/resources/application.yaml:
server:
  port: 8081
  servlet:
    context-path: /phonezone
spring:
  datasource:
    url: "jdbc:mysql://localhost:3306/phonezone"
    username: root
    password: ""
