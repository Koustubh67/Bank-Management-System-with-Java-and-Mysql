package com.koustubh.bank.service;

import com.koustubh.bank.domain.StaffRole;
import com.koustubh.bank.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class StaffServiceTest {

    @Autowired StaffService staff;

    @Test
    void newStaffGetATemporaryPasswordTheyMustChange() {
        StaffService.Created created = staff.create(" Ravi.Kumar ", "Ravi Kumar", StaffRole.OFFICER);
        assertThat(created.user().getUsername()).isEqualTo("ravi.kumar");
        assertThat(created.user().isMustChangePassword()).isTrue();
        assertThat(created.temporaryPassword()).matches("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

        assertThatThrownBy(() -> staff.create("ravi.kumar", "Someone", StaffRole.OFFICER)).hasMessageContaining("taken");
        assertThatThrownBy(() -> staff.create("x", "Someone", StaffRole.OFFICER)).isInstanceOf(InvalidRequestException.class);

        staff.changeOwnPassword("ravi.kumar", created.temporaryPassword(), "RaviNew2026");
        assertThat(staff.get("ravi.kumar").isMustChangePassword()).isFalse();
    }

    @Test
    void adminsCannotLockThemselvesOut() {
        Long adminId = staff.get("admin").getId();
        assertThatThrownBy(() -> staff.setActive(adminId, false, "admin")).hasMessageContaining("own login");
        assertThatThrownBy(() -> staff.resetPassword(adminId, "admin")).hasMessageContaining("Change password");
    }
}
