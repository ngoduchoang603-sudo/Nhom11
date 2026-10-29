package com.shop.user.config;

import com.shop.user.entity.GenderEnum;
import com.shop.user.entity.RoleEnum;
import com.shop.user.entity.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * DataInitializer — khoi tao du lieu mau khi app start.
 * <p>
 * Day la cach lam an toan hon viec dung import.sql, vi password
 * duoc hash bang chinh BcryptUtil ma app dung — dam bao login matches.
 * <p>
 * Su dung StartupEvent — observer pattern cua Quarkus, tuong duong
 * @PostConstruct nhung chay khi app san sang.
 */
@ApplicationScoped
public class DataInitializer {

    private static final Logger LOG = Logger.getLogger(DataInitializer.class);

    @Transactional
    void init(@Observes StartupEvent ev) {
        // Chi seed neu chua co user nao
        if (User.count() > 0) {
            LOG.info("Database da co data, bo qua step seed.");
            return;
        }

        LOG.info("Seeding du lieu mau...");

        // Tao admin: username=admin, password=admin123
        User admin = new User();
        admin.username = "admin";
        admin.password = BcryptUtil.bcryptHash("admin123");
        admin.fullName = "Quan tri vien";
        admin.email = "admin@shop.com";
        admin.phone = "0987654321";
        admin.status = "1";
        admin.role = RoleEnum.ADMIN;
        admin.gender = GenderEnum.NAM;
        admin.persist();

        // Tao user thuong: username=user01, password=123456
        User user = new User();
        user.username = "user01";
        user.password = BcryptUtil.bcryptHash("123456");
        user.fullName = "Nguyen Van A";
        user.email = "user01@gmail.com";
        user.phone = "0123456789";
        user.status = "1";
        user.role = RoleEnum.USER;
        user.gender = GenderEnum.NAM;
        user.persist();

        LOG.infof("Da seed %d users.", User.count());
    }
}
