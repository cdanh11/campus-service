package com.campus.academic.api;

import com.campus.testsupport.PostgresApplicationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import com.campus.academic.application.AcademicCatalogService;
import com.campus.academic.application.AcademicDeliveryService;
import com.campus.academic.application.EnrollmentService;
import com.campus.academic.domain.*;
import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.RoleRepository;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.OrganizationUnitStatus;
import com.campus.organization.domain.OrganizationUnitType;
import com.campus.personnel.application.FacultyStaffManagementService;
import com.campus.personnel.domain.PersonnelStatus;
import com.campus.personnel.domain.PersonnelType;
import com.campus.student.application.StudentManagementService;
import com.campus.student.domain.StudentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@PostgresApplicationTest
class AcademicRecordsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AcademicCatalogService catalog;
    @Autowired AcademicDeliveryService delivery;
    @Autowired EnrollmentService enrollments;
    @Autowired StudentManagementService students;
    @Autowired OrganizationUnitManagementService units;
    @Autowired FacultyStaffManagementService personnel;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwords;

    String prefix;
    String adminToken;
    UUID unit, program, generalCourse, projectCourse, finalCourse, faculty, term;
    UUID generalSection, projectSection, finalSection;
    UUID generalEnrollment, projectEnrollment, finalEnrollment;
    UUID studentId, studentAccount;
    String studentToken;

    @BeforeEach
    void setup() {
        prefix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        unit = units.create(prefix, "Records Unit", OrganizationUnitType.DEPARTMENT, OrganizationUnitStatus.ACTIVE).id();
        program = catalog.createProgram(prefix + "P", "Records Program", unit, AcademicCatalogStatus.ACTIVE).id();
        generalCourse = catalog.createCourse(prefix + "GEN", "General Course", 3, CourseGroup.GENERAL, false, unit,
                AcademicCatalogStatus.ACTIVE).id();
        projectCourse = catalog.createCourse(prefix + "PRJ", "Project Course", 4, CourseGroup.MAJOR, true, unit,
                AcademicCatalogStatus.ACTIVE).id();
        finalCourse = catalog.createCourse(prefix + "FIN", "Major Final Course", 3, CourseGroup.MAJOR, false, unit,
                AcademicCatalogStatus.ACTIVE).id();
        faculty = personnel.create(prefix, "Records Teacher", null, null, PersonnelType.FACULTY, unit,
                PersonnelStatus.ACTIVE).id();
        term = activeTerm(prefix + "T1", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 1, 15));
        generalSection = openSection(generalCourse, prefix + "G1");
        projectSection = openSection(projectCourse, prefix + "P1");
        finalSection = openSection(finalCourse, prefix + "F1");

        var account = account(prefix.toLowerCase(Locale.ROOT) + "-student@campus.example", RoleCode.USER);
        studentAccount = account.id();
        studentToken = tokens.accessToken(account);
        studentId = students.create(prefix + "S001", "Records Student", null, studentAccount, unit, StudentStatus.ACTIVE).id();
        generalEnrollment = enrollments.create(studentId, generalSection).id();
        projectEnrollment = enrollments.create(studentId, projectSection).id();
        finalEnrollment = enrollments.create(studentId, finalSection).id();
        adminToken = tokens.accessToken(account(prefix.toLowerCase(Locale.ROOT) + "-admin@campus.example", RoleCode.ADMIN));
    }

    @Test
    void recordsAndCorrectsGradesUsingTheWeightSchemeOfEachCourse() throws Exception {
        var general = read(perform(post(gradePath(generalEnrollment)).content(scores(8.0, 7.0, 9.0, null)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.scheme").value("GENERAL"))
                .andExpect(jsonPath("$.totalScore").value(8.1))
                .andExpect(jsonPath("$.letterGrade").value("B"))
                .andExpect(jsonPath("$.gradePoint").value(3.0))
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.finalScore").value(9.0))
                .andExpect(jsonPath("$.rowVersion").value(0)));
        assertThat(general.get("recordedBy").asText()).isNotBlank();
        read(perform(get(gradePath(generalEnrollment))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(general.get("id").asText())));

        perform(post(gradePath(projectEnrollment)).content(scores(9.0, 8.0, 8.0, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        perform(post(gradePath(projectEnrollment)).content(scores(9.0, 8.0, null, 8.0)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.scheme").value("MAJOR_PROJECT"))
                .andExpect(jsonPath("$.totalScore").value(8.1)).andExpect(jsonPath("$.projectScore").value(8.0));

        perform(post(gradePath(finalEnrollment)).content(scores(8.0, 6.0, 9.0, null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.scheme").value("MAJOR_FINAL"))
                .andExpect(jsonPath("$.totalScore").value(8.0));

        perform(post(gradePath(generalEnrollment)).content(scores(5.0, 5.0, 5.0, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GRADE_ALREADY_RECORDED"));

        perform(put(gradePath(generalEnrollment)).content("{\"attendanceScore\":9.0,\"midtermScore\":9.0,\"finalScore\":9.0,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalScore").value(9.0))
                .andExpect(jsonPath("$.letterGrade").value("A")).andExpect(jsonPath("$.rowVersion").value(1));
        perform(put(gradePath(generalEnrollment)).content("{\"attendanceScore\":9.5,\"midtermScore\":9.5,\"finalScore\":9.5,\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        perform(put(gradePath(generalEnrollment)).content("{\"attendanceScore\":9.5,\"midtermScore\":9.5,\"finalScore\":9.5,\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(general.get("id").asText()));
    }

    @Test
    void validatesGradeRequestsAndProtectsTheEndpoints() throws Exception {
        String userToken = tokens.accessToken(account(prefix.toLowerCase(Locale.ROOT) + "-plain@campus.example", RoleCode.USER));
        for (String token : List.of("", userToken)) {
            var request = post(gradePath(generalEnrollment)).content(scores(5.0, 5.0, 5.0, null));
            if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().is(token.isEmpty() ? 401 : 403));
        }
        for (String body : List.of("{}", "{\"attendanceScore\":11.0,\"midtermScore\":5.0,\"finalScore\":5.0}", "{")) {
            perform(post(gradePath(generalEnrollment)).content(body)).andExpect(status().isBadRequest());
        }
        error(perform(post(gradePath(UUID.randomUUID())).content(scores(5.0, 5.0, 5.0, null))), 404);
        error(perform(post("/api/v1/admin/academic/enrollments/not-a-uuid/grade").content(scores(5.0, 5.0, 5.0, null))), 400);
        for (String query : List.of("page=-1", "size=0", "size=101", "sort=id,asc", "sort=totalScore,bad", "enrollmentId=bad")) {
            perform(get("/api/v1/admin/academic/grades?" + query)).andExpect(status().isBadRequest());
        }
        perform(post(gradePath(generalEnrollment)).content(scores(8.0, 7.0, 9.0, null))).andExpect(status().isCreated());
        perform(get("/api/v1/admin/academic/grades").param("enrollmentId", generalEnrollment.toString())
                .param("sort", "totalScore,desc")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].enrollmentId").value(generalEnrollment.toString()));
        error(perform(get("/api/v1/admin/academic/enrollments/" + UUID.randomUUID() + "/grade")), 404);
    }

    @Test
    void exposesTranscriptGpaAndImprovementAttemptsToTheStudent() throws Exception {
        assignProfile("K23", program);
        recordGeneral(generalEnrollment, 8.0, 7.0, 9.0);
        recordMajorFinal(finalEnrollment, 3.0, 3.0, 3.0);

        var transcript = read(perform(get("/api/v1/me/transcript").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentId.toString()))
                .andExpect(jsonPath("$.programId").value(program.toString()))
                .andExpect(jsonPath("$.programName").value("Records Program"))
                .andExpect(jsonPath("$.cohort").value("K23"))
                .andExpect(jsonPath("$.terms[0].gpa").value(1.5))
                .andExpect(jsonPath("$.terms[0].credits").value(6))
                .andExpect(jsonPath("$.terms[0].passedCredits").value(3))
                .andExpect(jsonPath("$.terms[0].gradedCourses").value(2))
                .andExpect(jsonPath("$.terms[0].classification").value("YEU"))
                .andExpect(jsonPath("$.summary.cumulativeGpa").value(1.5))
                .andExpect(jsonPath("$.summary.cumulativeCredits").value(6))
                .andExpect(jsonPath("$.summary.gradedCourses").value(2))
                .andExpect(jsonPath("$.summary.classification").value("YEU"))
                .andExpect(jsonPath("$.summary.latestTermGpa").value(1.5))
                .andExpect(jsonPath("$.summary.latestTermClassification").value("YEU")));
        assertThat(transcript.get("records")).hasSize(2);
        var general = recordOf(transcript, "GEN");
        assertThat(general.get("scheme").asText()).isEqualTo("GENERAL");
        assertThat(general.get("attendanceWeight").asDouble()).isEqualTo(0.1);
        assertThat(general.get("midtermWeight").asDouble()).isEqualTo(0.4);
        assertThat(general.get("finalWeight").asDouble()).isEqualTo(0.5);
        assertThat(general.get("projectWeight").asDouble()).isZero();
        assertThat(general.get("retake").asBoolean()).isFalse();
        assertThat(general.get("improvement").asBoolean()).isFalse();
        var major = recordOf(transcript, "FIN");
        assertThat(major.get("scheme").asText()).isEqualTo("MAJOR_FINAL");
        assertThat(major.get("midtermWeight").asDouble()).isEqualTo(0.3);
        assertThat(major.get("finalWeight").asDouble()).isEqualTo(0.6);

        var retakeTerm = activeTerm(prefix + "T2", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 15));
        var retakeSection = openSectionIn(retakeTerm, generalCourse, prefix + "G2");
        var retake = enrollments.create(studentId, retakeSection).id();
        recordGeneral(retake, 9.5, 9.5, 9.5);

        var improved = read(perform(get("/api/v1/me/transcript").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terms[0].gpa").value(4.0))
                .andExpect(jsonPath("$.summary.cumulativeGpa").value(2.0))
                .andExpect(jsonPath("$.summary.cumulativeCredits").value(6))
                .andExpect(jsonPath("$.summary.gradedCourses").value(2))
                .andExpect(jsonPath("$.summary.latestTermGpa").value(4.0))
                .andExpect(jsonPath("$.summary.latestTermClassification").value("XUAT_SAC")));
        assertThat(improved.get("records")).hasSize(3);
        var latest = recordFor(improved, retake.toString());
        assertThat(latest.get("retake").asBoolean()).isTrue();
        assertThat(latest.get("improvement").asBoolean()).isTrue();
        assertThat(recordFor(improved, generalEnrollment.toString()).get("retake").asBoolean()).isFalse();
    }

    @Test
    void buildsTheCurriculumFlowFromBestAttemptsAndPrerequisites() throws Exception {
        assignProfile("K23", program);
        enrollments.update(finalEnrollment, EnrollmentStatus.WITHDRAWN, 0);
        var first = curriculum(program, generalCourse, 1, null);
        curriculum(program, projectCourse, 2, generalCourse);
        curriculum(program, finalCourse, 3, projectCourse);
        recordGeneral(generalEnrollment, 8.0, 7.0, 9.0);

        var flow = read(perform(get("/api/v1/me/curriculum").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").value(program.toString()))
                .andExpect(jsonPath("$.cohort").value("K23"))
                .andExpect(jsonPath("$.semesters[0].index").value(1))
                .andExpect(jsonPath("$.semesters[0].courses[0].code").value(prefix + "GEN"))
                .andExpect(jsonPath("$.semesters[0].courses[0].status").value("PASSED"))
                .andExpect(jsonPath("$.semesters[0].courses[0].bestLetter").value("B"))
                .andExpect(jsonPath("$.semesters[1].courses[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.semesters[1].courses[0].prerequisiteCode").value(prefix + "GEN"))
                .andExpect(jsonPath("$.semesters[1].courses[0].prerequisiteSatisfied").value(true))
                .andExpect(jsonPath("$.semesters[2].courses[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.semesters[2].courses[0].prerequisiteSatisfied").value(false)));
        assertThat(flow.get("semesters")).hasSize(3);

        var retakeTerm = activeTerm(prefix + "T3", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 15));
        var retake = enrollments.create(studentId, openSectionIn(retakeTerm, generalCourse, prefix + "G3")).id();
        recordGeneral(retake, 9.0, 9.0, 9.0);
        var upgraded = read(perform(get("/api/v1/me/curriculum").header("Authorization", "Bearer " + studentToken)));
        var general = upgraded.at("/semesters/0/courses/0");
        assertThat(general.get("status").asText()).isEqualTo("PASSED");
        assertThat(general.get("bestTotal").asDouble()).isEqualTo(9.0);
        assertThat(general.get("improvementAttempted").asBoolean()).isTrue();
        assertThat(first.get("id").asText()).isNotBlank();
    }

    @Test
    void requiresAProgramBeforeServingACurriculum() throws Exception {
        perform(get("/api/v1/me/curriculum").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACADEMIC_CURRICULUM_NOT_AVAILABLE"));
        perform(get("/api/v1/me/transcript").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.programId").doesNotExist())
                .andExpect(jsonPath("$.terms").isEmpty());
    }

    @Test
    void managesCurriculumEntriesWithVersioningAndValidation() throws Exception {
        var entry = curriculum(program, generalCourse, 1, null);
        var second = curriculum(program, projectCourse, 2, generalCourse);
        error(perform(post("/api/v1/admin/academic/curriculum").content(curriculumBody(program, generalCourse, 3, null))), 409);
        for (String body : List.of("{}", curriculumBody(program, finalCourse, 0, null),
                "{\"programId\":\"" + program + "\",\"courseId\":\"" + finalCourse + "\",\"semesterIndex\":1,\"prerequisiteCourseId\":\"" + finalCourse + "\"}")) {
            error(perform(post("/api/v1/admin/academic/curriculum").content(body)), 400);
        }
        error(perform(post("/api/v1/admin/academic/curriculum").content(curriculumBody(UUID.randomUUID(), finalCourse, 1, null))), 404);
        error(perform(post("/api/v1/admin/academic/curriculum").content(curriculumBody(program, UUID.randomUUID(), 1, null))), 404);
        error(perform(post("/api/v1/admin/academic/curriculum").content(curriculumBody(program, finalCourse, 1, UUID.randomUUID()))), 404);
        perform(get("/api/v1/admin/academic/curriculum").param("programId", program.toString()).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].courseCode").value(prefix + "PRJ"))
                .andExpect(jsonPath("$.content[0].prerequisiteCode").value(prefix + "GEN"));
        perform(put("/api/v1/admin/academic/curriculum/" + second.get("id").asText())
                .content("{\"semesterIndex\":3,\"prerequisiteCourseId\":null,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.semesterIndex").value(3)).andExpect(jsonPath("$.rowVersion").value(1));
        perform(put("/api/v1/admin/academic/curriculum/" + second.get("id").asText())
                .content("{\"semesterIndex\":3,\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        perform(put("/api/v1/admin/academic/curriculum/" + entry.get("id").asText())
                .content("{\"semesterIndex\":1,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(1));
        error(perform(get("/api/v1/admin/academic/curriculum/" + UUID.randomUUID())), 404);
        for (String query : List.of("page=-1", "size=0", "size=101")) {
            perform(get("/api/v1/admin/academic/curriculum?programId=" + program + "&" + query)).andExpect(status().isBadRequest());
        }
        perform(get("/api/v1/admin/academic/curriculum?programId=" + UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACADEMIC_PROGRAM_NOT_FOUND"));
    }

    @Test
    void administersCohortAndProgramThroughTheStudentProfile() throws Exception {
        var initial = read(perform(get("/api/v1/admin/students/" + studentId + "/profile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.cohort").doesNotExist()).andExpect(jsonPath("$.profileVersion").value(0)));
        assertThat(initial.get("programId").isNull()).isTrue();

        perform(put("/api/v1/admin/students/" + studentId + "/profile")
                .content("{\"cohort\":\"K23\",\"programId\":\"" + program + "\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cohort").value("K23"))
                .andExpect(jsonPath("$.programId").value(program.toString()))
                .andExpect(jsonPath("$.programName").value("Records Program"))
                .andExpect(jsonPath("$.profileVersion").value(0));
        perform(put("/api/v1/admin/students/" + studentId + "/profile")
                .content("{\"cohort\":\"K24\",\"programId\":\"" + program + "\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cohort").value("K24"))
                .andExpect(jsonPath("$.profileVersion").value(1));
        perform(put("/api/v1/admin/students/" + studentId + "/profile")
                .content("{\"cohort\":\"K23\",\"programId\":\"" + program + "\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROFILE_CONCURRENT_MODIFICATION"));
        error(perform(put("/api/v1/admin/students/" + studentId + "/profile")
                .content("{\"cohort\":\"K2\",\"expectedVersion\":1}")), 400);
        perform(put("/api/v1/admin/students/" + studentId + "/profile")
                .content("{\"cohort\":\"K24\",\"programId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACADEMIC_PROGRAM_UNAVAILABLE"));
        error(perform(get("/api/v1/admin/students/" + UUID.randomUUID() + "/profile")), 404);
        mvc.perform(get("/api/v1/admin/students/" + studentId + "/profile").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    private void assignProfile(String cohort, UUID programId) throws Exception {
        perform(put("/api/v1/admin/students/" + studentId + "/profile")
                .content("{\"cohort\":\"" + cohort + "\",\"programId\":\"" + programId + "\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());
    }

    private void recordGeneral(UUID enrollment, double attendance, double midterm, double finalScore) throws Exception {
        perform(post(gradePath(enrollment)).content(scores(attendance, midterm, finalScore, null))).andExpect(status().isCreated());
    }

    private void recordMajorFinal(UUID enrollment, double attendance, double midterm, double finalScore) throws Exception {
        perform(post(gradePath(enrollment)).content(scores(attendance, midterm, finalScore, null))).andExpect(status().isCreated());
    }

    private JsonNode curriculum(UUID programId, UUID courseId, int semester, UUID prerequisite) throws Exception {
        return read(perform(post("/api/v1/admin/academic/curriculum")
                .content(curriculumBody(programId, courseId, semester, prerequisite))).andExpect(status().isCreated()));
    }

    private String curriculumBody(UUID programId, UUID courseId, int semester, UUID prerequisite) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("programId", programId);
        body.put("courseId", courseId);
        body.put("semesterIndex", semester);
        if (prerequisite != null) body.put("prerequisiteCourseId", prerequisite);
        return json.writeValueAsString(body);
    }

    private static JsonNode recordOf(JsonNode transcript, String suffix) {
        for (var record : transcript.get("records")) if (record.at("/course/code").asText().endsWith(suffix)) return record;
        throw new AssertionError("Missing transcript record " + suffix);
    }

    private static JsonNode recordFor(JsonNode transcript, String enrollmentId) {
        for (var record : transcript.get("records")) if (record.get("enrollmentId").asText().equals(enrollmentId)) return record;
        throw new AssertionError("Missing transcript record " + enrollmentId);
    }

    private String gradePath(UUID enrollment) { return "/api/v1/admin/academic/enrollments/" + enrollment + "/grade"; }

    private String scores(double attendance, double midterm, Double finalScore, Double projectScore) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("attendanceScore", attendance);
        body.put("midtermScore", midterm);
        if (finalScore != null) body.put("finalScore", finalScore);
        if (projectScore != null) body.put("projectScore", projectScore);
        return json.writeValueAsString(body);
    }

    private UUID activeTerm(String code, LocalDate start, LocalDate end) {
        var value = delivery.createTerm(code, "Term " + code, start, end);
        return delivery.updateTerm(value.id(), value.code(), value.name(), start, end, AcademicTermStatus.ACTIVE, 0).id();
    }

    private UUID openSection(UUID courseId, String code) { return openSectionIn(term, courseId, code); }

    private UUID openSectionIn(UUID termId, UUID courseId, String code) {
        var offering = delivery.createOffering(termId, courseId);
        var open = delivery.updateOffering(offering.id(), AcademicDeliveryStatus.OPEN, 0);
        var schedule = SectionSchedule.of(2, LocalTime.of(7, 30), LocalTime.of(9, 30), "R" + code);
        var section = delivery.createSection(open.id(), code, 60, faculty, schedule);
        return delivery.updateSection(section.id(), section.code(), section.capacity(), faculty, schedule,
                AcademicDeliveryStatus.OPEN, 0).id();
    }

    private UserAccount account(String email, RoleCode role) {
        return users.save(UserAccount.create(UUID.randomUUID(), email, email, passwords.encode("valid-password"),
                AccountStatus.ACTIVE, Set.of(roles.findByCode(role).orElseThrow()), Instant.now()));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private void error(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.code").exists()).andExpect(jsonPath("$.path").exists());
    }
}
