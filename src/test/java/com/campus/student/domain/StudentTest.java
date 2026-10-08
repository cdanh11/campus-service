package com.campus.student.domain;
import java.time.Instant; import java.util.UUID; import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.*;
class StudentTest { @Test void normalizesStudentValuesAndRejectsInvalidValues(){ Student student=Student.create(UUID.randomUUID()," S001 "," Student One ","ONE@campus.example",null,UUID.randomUUID(),StudentStatus.ACTIVE,Instant.now()); assertThat(student.studentNumber()).isEqualTo("S001"); assertThat(student.fullName()).isEqualTo("Student One"); assertThat(student.email()).isEqualTo("one@campus.example"); assertThat(student.identityUserId()).isNull(); assertThatThrownBy(()->Student.create(UUID.randomUUID(),"x","Name",null,null,UUID.randomUUID(),StudentStatus.ACTIVE,Instant.now())).isInstanceOf(Student.InvalidStudentException.class); assertThatThrownBy(()->Student.create(UUID.randomUUID(),"S001","Name","invalid",null,UUID.randomUUID(),StudentStatus.ACTIVE,Instant.now())).isInstanceOf(Student.InvalidStudentException.class); }
    @Test
    void boundsTheCanonicalNumberAfterUnicodeUppercaseExpansion() {
        assertThat(Student.create(UUID.randomUUID(), "ß".repeat(16), "Student One", null, null,
                UUID.randomUUID(), StudentStatus.ACTIVE, Instant.now()).studentNumber()).isEqualTo("SS".repeat(16));
        assertThatThrownBy(() -> Student.create(UUID.randomUUID(), "ß".repeat(17), "Student One", null, null,
                UUID.randomUUID(), StudentStatus.ACTIVE, Instant.now())).isInstanceOf(Student.InvalidStudentException.class);
    }
}
