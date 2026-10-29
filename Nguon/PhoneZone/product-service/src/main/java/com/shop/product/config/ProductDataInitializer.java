package com.shop.product.config;

import com.shop.product.entity.Product;
import com.shop.product.entity.ProductVariant;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
     * Seed du lieu san pham mau vao PostgreSQL khi app start.
 */
@ApplicationScoped
public class ProductDataInitializer {

    private static final Logger LOG = Logger.getLogger(ProductDataInitializer.class);

    @Transactional
    void init(@Observes StartupEvent ev) {
        // Du lieu co tu truoc khi them @Version co gia tri NULL. Hibernate khong
        // the tang version NULL khi cap nhat ton kho, nen chuan hoa mot lan luc start.
        long normalized = Product.update("version = 0 where version is null");
        if (normalized > 0) {
            LOG.infof("Da khoi tao version cho %d san pham cu.", normalized);
        }
        if (Product.count() > 0) {
            // Bo sung du lieu phien ban cho database demo cu ma khong ghi de san pham da nhap tu admin.
            for (Product product : Product.<Product>listAll()) {
                if (product.availableColors == null || product.availableColors.isBlank()) {
                    product.availableColors = defaultColors(product.category);
                }
                ensureVariants(product);
            }
            LOG.info("PostgreSQL da co data, bo qua step seed.");
            return;
        }

        LOG.info("Seeding du lieu san pham mau vao PostgreSQL...");

        List<Product> samples = new ArrayList<>();

        samples.add(makeProduct("iPhone 15 Pro Max 256GB", 32990000L, 2000000L, "Apple",
                "/img/1734122800899_15prm.jpg", 50L, "6.7 inch Super Retina XDR",
                "256GB", "8GB", "4422 mAh", "Titan tự nhiên, Xanh, Trắng, Đen"));

        samples.add(makeProduct("iPhone 14 Pro Max 256GB", 24990000L, 1500000L, "Apple",
                "/img/1734122744624_14prm.jpg", 30L, "6.1 inch Super Retina XDR",
                "128GB", "6GB", "3200 mAh", "Tím, Vàng, Bạc, Đen"));

        samples.add(makeProduct("iPhone 13 128GB", 18990000L, 1000000L, "Apple",
                "/img/1731654361073_ip13.png", 100L, "6.1 inch Super Retina XDR",
                "128GB", "4GB", "3240 mAh", "Hồng, Xanh, Trắng, Đen"));

        samples.add(makeProduct("Samsung Galaxy S24 Ultra", 31990000L, 3000000L, "SamSung",
                "/img/1731646750376_s24.jpg", 40L, "6.8 inch Dynamic AMOLED 2X",
                "256GB", "12GB", "5000 mAh", "Titan Xám, Titan Đen, Titan Tím"));

        samples.add(makeProduct("Samsung Galaxy S23", 19990000L, 2000000L, "SamSung",
                "/img/1731646796428_s23.webp", 60L, "6.1 inch Dynamic AMOLED 2X",
                "128GB", "8GB", "3900 mAh", "Kem, Tím, Đen, Xanh"));

        samples.add(makeProduct("Xiaomi 14 Pro 256GB", 22990000L, 1500000L, "Xiaomi",
                "/img/1731651551100_ip16.jpg", 25L, "6.73 inch LTPO AMOLED",
                "256GB", "12GB", "4880 mAh", "Đen, Trắng, Xanh"));

        samples.add(makeProduct("Oppo Find X7 Ultra", 18990000L, 1000000L, "Oppo",
                "/img/1731688064836_ip16.jpg", 35L, "6.78 inch AMOLED",
                "256GB", "12GB", "5000 mAh", "Đen, Xanh, Nâu"));

        samples.add(makeProduct("Vivo V30 Pro 5G", 15990000L, 500000L, "Vivo",
                "/img/1731654437893_ip16.jpg", 80L, "6.78 inch AMOLED",
                "256GB", "12GB", "5000 mAh", "Đen, Xanh ngọc, Hồng"));

        Product.persist(samples);
        Product.flush();
        samples.forEach(this::ensureVariants);
        LOG.infof("Da seed %d products vao PostgreSQL.", samples.size());
    }

    private Product makeProduct(String name, Long price, Long discount, String category,
                                String image, Long stock, String screen,
                                String storage, String ram, String battery, String colors) {
        Product p = new Product();
        p.name = name;
        p.price = price;
        p.discount = discount;
        p.category = category;
        p.image = image;
        p.stock = stock;
        p.screen = screen;
        p.storage = storage;
        p.ram = ram;
        p.battery = battery;
        p.availableColors = colors;
        p.status = "1";
        p.description = "Smartphone " + name + " chinh hang, bao hanh 12 thang.";
        p.createDate = Instant.now();
        p.comments = new ArrayList<>();
        return p;
    }

    private String defaultColors(String category) {
        if ("Apple".equalsIgnoreCase(category)) return "Đen, Trắng, Xanh";
        if ("SamSung".equalsIgnoreCase(category)) return "Đen, Tím, Kem";
        return "Đen, Xanh, Trắng";
    }

    /** Creates deterministic demo variants once; later staff changes are preserved. */
    private void ensureVariants(Product product) {
        if (product.id == null || ProductVariant.count("productId", product.id) > 0) return;
        String[] colors = (product.availableColors == null ? defaultColors(product.category) : product.availableColors).split(",");
        long total = product.stock == null ? 0L : product.stock;
        long base = colors.length == 0 ? total : total / colors.length;
        for (int i = 0; i < colors.length; i++) {
            ProductVariant variant = new ProductVariant();
            variant.productId = product.id;
            variant.color = colors[i].trim();
            variant.ram = product.ram;
            variant.storage = product.storage;
            variant.sku = "PZ-" + product.id + "-" + (i + 1);
            variant.stock = base + (i == 0 ? total % colors.length : 0);
            variant.priceAdjustment = 0L;
            variant.persist();
        }
    }
}
