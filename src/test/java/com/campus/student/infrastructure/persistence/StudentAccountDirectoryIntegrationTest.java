package com.campus.student.infrastructure.persistence;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.campus.identity.domain.*;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.*;
import com.campus.student.application.StudentAccountDirectory;
import com.campus.student.application.StudentManagementService;
import com.campus.student.domain.*;
import com.campus.testsupport.PostgresApplicationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @PostgresApplicationTest
class StudentAccountDirectoryIntegrationTest {
    @Autowired StudentAccountDirectory directory;
    @Autowired StudentManagementService students;
    @Autowired OrganizationUnitManagementService organizations;
    @Autowired UserAccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test void resolvesOnlyCurrentLinkWithoutExposingProfileDataOrRequiringActiveStatus() {
        var account = account(); var student = student(account.id());
        assertThat(directory.findByAccount(account.id())).contains(new StudentAccountDirectory.LinkedStudent(student.id(),StudentStatus.ACTIVE));
        assertThat(directory.findByAccount(UUID.randomUUID())).isEmpty();
        assertThatThrownBy(() -> directory.findByAccount(null)).isInstanceOf(NullPointerException.class);
        jdbc.update("UPDATE students SET status='INACTIVE' WHERE id=?",student.id());
        assertThat(directory.findByAccount(account.id())).contains(new StudentAccountDirectory.LinkedStudent(student.id(),StudentStatus.INACTIVE));
        jdbc.update("UPDATE students SET identity_user_id=NULL WHERE id=?",student.id());
        assertThat(directory.findByAccount(account.id())).isEmpty();
        assertThat(students.get(student.id()).identityUserId()).isNull();
    }
    @Test void seesCommittedDeactivationAndUnlinkDespiteCachedStudentInCallerTransaction() throws Exception {
        var account = account(); var student = student(account.id());
        var worker = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                assertThat(students.get(student.id()).status()).isEqualTo(StudentStatus.ACTIVE);
                try {
                    worker.submit(() -> jdbc.update("UPDATE students SET status='INACTIVE' WHERE id=?",student.id())).get(10,TimeUnit.SECONDS);
                    assertThat(directory.findByAccount(account.id())).contains(new StudentAccountDirectory.LinkedStudent(student.id(),StudentStatus.INACTIVE));
                    worker.submit(() -> jdbc.update("UPDATE students SET identity_user_id=NULL WHERE id=?",student.id())).get(10,TimeUnit.SECONDS);
                    assertThat(directory.findByAccount(account.id())).isEmpty();
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
        } finally {
            worker.shutdownNow(); assertThat(worker.awaitTermination(10,TimeUnit.SECONDS)).isTrue();
        }
    }
    private UserAccount account() {
        var id = UUID.randomUUID();
        return accounts.save(UserAccount.create(id,id+"@campus.example","Test linked account",passwords.encode("test-only-placeholder"),
                AccountStatus.ACTIVE,Set.of(roles.findByCode(RoleCode.USER).orElseThrow()),Instant.now()));
    }
    private Student student(UUID account) {
        var code = UUID.randomUUID().toString().replace("-","").substring(0,20);
        var unit = organizations.create("U"+code,"Test unit",OrganizationUnitType.FACULTY,OrganizationUnitStatus.ACTIVE);
        return students.create("S"+code,"Linked Student",null,account,unit.id(),StudentStatus.ACTIVE);
    }
}
