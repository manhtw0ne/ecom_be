package com.manh.ecom_be.services.product;

import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@ActiveProfiles("test") @Transactional
class CatalogQueryBudgetTest {
    @Autowired ProductService service;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired ProductImageRepository images;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    @Test void paginatedCatalogHasBoundedQueriesIncludingSerialization() throws Exception {
        String marker = "query-budget-" + System.nanoTime();
        for (int i = 0; i < 12; i++) {
            var category = categories.save(Category.builder().name(marker + i).build());
            var product = products.save(Product.builder().name(marker + i).category(category)
                    .price(BigDecimal.TEN).stockQuantity(5).build());
            images.save(ProductImage.builder().product(product).imageUrl("fixture-" + i + ".png").build());
        }
        em.flush();
        em.clear();
        var stats = em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.clear();
        var page = service.getAllProducts(marker, 0L, PageRequest.of(0, 10, Sort.by("id")));
        String serialized = json.writeValueAsString(page.getContent());
        long queries = stats.getPrepareStatementCount();
        System.out.println("CATALOG_QUERY_COUNT=" + queries);
        assertThat(queries).isLessThanOrEqualTo(5);
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getTotalElements()).isEqualTo(12);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(serialized).contains("fixture-");
    }
}
