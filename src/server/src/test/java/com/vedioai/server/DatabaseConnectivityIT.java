package com.vedioai.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest(properties = {
        "vedioai.auth.mail.delivery-mode=local-catcher",
        "vedioai.auth.mail.dispatcher-enabled=false"
})
class DatabaseConnectivityIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void connectsAsApplicationAccountWithUtcSession() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            try (ResultSet rs = statement.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }

            try (ResultSet rs = statement.executeQuery(
                    "SELECT DATABASE(), CURRENT_USER(), @@session.time_zone")) {
                assertTrue(rs.next());
                assertEquals("vedioai", rs.getString(1));

                String currentUser = rs.getString(2);
                assertNotNull(currentUser);
                assertTrue(currentUser.startsWith("vedioai@"),
                        "unexpected DB user: " + currentUser);
                assertEquals("+00:00", rs.getString(3));
            }
        }
    }
}
