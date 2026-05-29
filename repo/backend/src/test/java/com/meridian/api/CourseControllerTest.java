package com.meridian.api;

import com.meridian.dto.CreateCourseRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseControllerTest extends TestContainersBase {

    private static final String COURSES_URL = "/api/courses";

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAs("admin", "Admin@12345678");
        studentToken = loginAs("student1", "Student@12345678");
    }

    // Test 1: GET /api/courses as authenticated user returns 200
    @Test
    void listCourses_authenticated_returns200() throws Exception {
        mockMvc.perform(get(COURSES_URL)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // Test 2: GET /api/courses unauthenticated returns 401
    @Test
    void listCourses_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(COURSES_URL))
                .andExpect(status().isUnauthorized());
    }

    // Test 3: POST /api/courses as STUDENT returns 403
    @Test
    void createCourse_asStudent_returns403() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest(
                "Unauthorized Course",
                "1.0",
                "Online",
                "instructor1",
                30
        );

        mockMvc.perform(post(COURSES_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // Test 4: POST /api/courses as ADMINISTRATOR with valid body returns 201
    @Test
    void createCourse_asAdmin_withValidBody_returns201() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest(
                "Advanced Safety Training",
                "2.0",
                "Building A",
                "admin",
                25
        );

        mockMvc.perform(post(COURSES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Advanced Safety Training"));
    }

    // Test 5: POST /api/courses with blank title returns 400
    @Test
    void createCourse_blankTitle_returns400() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest(
                "",
                "1.0",
                "Room 1",
                "instructor",
                10
        );

        mockMvc.perform(post(COURSES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Test 6: GET /api/courses/{id} with unknown UUID returns 404
    @Test
    void getCourse_unknownId_returns404() throws Exception {
        String unknownId = UUID.randomUUID().toString();

        mockMvc.perform(get(COURSES_URL + "/" + unknownId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    // Test 7: PUT /api/courses/{id} as ADMIN → 200 with updated title
    @Test
    void updateCourse_asAdmin_returns200WithUpdatedTitle() throws Exception {
        // Create a course first
        CreateCourseRequest createRequest = new CreateCourseRequest(
                "Course To Update", "1.0", "Lab 2", "faculty1", 20);
        MvcResult createResult = mockMvc.perform(post(COURSES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String courseId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        CreateCourseRequest updateRequest = new CreateCourseRequest(
                "Updated Course Title", "2.0", "Lab 3", "faculty1", 30);
        mockMvc.perform(put(COURSES_URL + "/" + courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(courseId))
                .andExpect(jsonPath("$.title").value("Updated Course Title"))
                .andExpect(jsonPath("$.version").value("2.0"));
    }

    // Test 8: PUT /api/courses/{id} as STUDENT → 403
    @Test
    void updateCourse_asStudent_returns403() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest(
                "Some Update", "1.0", "Room 1", "faculty1", 10);
        mockMvc.perform(put(COURSES_URL + "/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // Test 9: PUT /api/courses/{id} with unknown ID as ADMIN → 404
    @Test
    void updateCourse_unknownId_returns404() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest(
                "Nonexistent Update", "1.0", "Room X", "faculty1", 10);
        mockMvc.perform(put(COURSES_URL + "/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
