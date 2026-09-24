package com.koustubh.bank.demo;

import com.koustubh.bank.demo.DemoDataSeeder.DemoCustomer;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.CustomerRepository;
import com.koustubh.bank.service.CardSecurityService;
import com.koustubh.bank.service.CustomerLoginService;
import com.koustubh.bank.service.UpiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks that every login listed in the README really works. Uses its own in-memory database. */
@SpringBootTest(properties = {
        "bank.demo.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class DemoDataSeederTest {

    @Autowired DemoDataSeeder seeder;
    @Autowired CardSecurityService cardSecurity;
    @Autowired UpiService upi;
    @Autowired CustomerLoginService customerLogin;
    @Autowired com.koustubh.bank.service.WealthService wealth;
    @Autowired AccountRepository accounts;
    @Autowired CustomerRepository customers;

    private BigDecimal balance(DemoCustomer d) {
        return accounts.findById(accounts.findIdByAccountNumber(d.accountNumber()).orElseThrow()).orElseThrow().getBalance();
    }

    @Test
    void demoLoginsFromTheReadmeWork() {
        cardSecurity.verifyLogin(DemoDataSeeder.RAHUL.cardNumber(), "1234");
        cardSecurity.verifyLogin(DemoDataSeeder.PRIYA.cardNumber(), "2345");
        assertThat(upi.balance("rahul.0001@javabank", "123456")).isPositive();
        assertThat(upi.balance("priya.0002@javabank", "234567")).isPositive();
        for (DemoCustomer d : DemoDataSeeder.ALL) {
            assertThat(customerLogin.verifyLogin(d.customerId(), DemoDataSeeder.DEMO_PASSWORD)).isEqualTo(d.customerId());
        }
        assertThat(DemoDataSeeder.RAHUL.customerId()).isEqualTo("JB10000001");

        assertThatThrownBy(() -> cardSecurity.verifyLogin(DemoDataSeeder.AMIT.cardNumber(), "3456"))
                .isInstanceOf(DisabledException.class).hasMessageContaining("approval");
        assertThatThrownBy(() -> cardSecurity.verifyLogin(DemoDataSeeder.SNEHA.cardNumber(), "4567"))
                .isInstanceOf(DisabledException.class).hasMessageContaining("frozen");
        assertThatThrownBy(() -> cardSecurity.verifyLogin(DemoDataSeeder.VIKRAM.cardNumber(), "5678"))
                .isInstanceOf(LockedException.class);
        assertThatThrownBy(() -> upi.balance("anjali.0006@javabank", "345678"))
                .hasMessageContaining("locked");
    }

    @Test
    void balancesMatchTheReadme() {
        assertThat(balance(DemoDataSeeder.RAHUL)).isEqualByComparingTo("39451");
        assertThat(balance(DemoDataSeeder.PRIYA)).isEqualByComparingTo("62550");
        assertThat(balance(DemoDataSeeder.AMIT)).isEqualByComparingTo("0");
        assertThat(balance(DemoDataSeeder.SNEHA)).isEqualByComparingTo("20000");
        assertThat(balance(DemoDataSeeder.VIKRAM)).isEqualByComparingTo("15000");
        assertThat(balance(DemoDataSeeder.ANJALI)).isEqualByComparingTo("7700");
    }

    @Test
    void demoCustomersHaveInvestmentsAndInsurance() {
        assertThat(wealth.portfolio(DemoDataSeeder.RAHUL.customerId()).investments()).hasSize(1);
        assertThat(wealth.portfolio(DemoDataSeeder.RAHUL.customerId()).policies()).hasSize(1);
        assertThat(wealth.portfolio(DemoDataSeeder.PRIYA.customerId()).fdMaturityValue()).isPositive();
    }

    @Test
    void runningAgainChangesNothing() throws Exception {
        long before = customers.count();
        seeder.run(null);
        assertThat(customers.count()).isEqualTo(before);
    }
}
