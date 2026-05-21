package com.meridian.service;

import com.meridian.dto.CourseDto;
import com.meridian.dto.CreateCourseRequest;
import com.meridian.entity.Course;
import com.meridian.exception.AppException;
import com.meridian.repository.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public Page<CourseDto> listCourses(Pageable pageable) {
        return courseRepository.findAll(pageable).map(this::toDto);
    }

    public CourseDto createCourse(CreateCourseRequest request) {
        Course course = new Course();
        course.setTitle(request.title());
        course.setVersion(request.version() != null ? request.version() : "1.0");
        course.setLocation(request.location());
        course.setInstructor(request.instructor());
        course.setCapacity(request.capacity());
        Course saved = courseRepository.save(course);
        log.info("Course created: id={}, title={}", saved.getId(), saved.getTitle());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public CourseDto getCourse(UUID id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Course not found: " + id));
        return toDto(course);
    }

    public CourseDto updateCourse(UUID id, CreateCourseRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Course not found: " + id));

        if (request.title() != null && !request.title().isBlank()) {
            course.setTitle(request.title());
        }
        if (request.version() != null) {
            course.setVersion(request.version());
        }
        if (request.location() != null) {
            course.setLocation(request.location());
        }
        if (request.instructor() != null) {
            course.setInstructor(request.instructor());
        }
        if (request.capacity() != null) {
            course.setCapacity(request.capacity());
        }

        Course saved = courseRepository.save(course);
        log.info("Course updated: id={}", id);
        return toDto(saved);
    }

    private CourseDto toDto(Course course) {
        return new CourseDto(
                course.getId(),
                course.getTitle(),
                course.getVersion(),
                course.getLocation(),
                course.getInstructor()
        );
    }
}
