package com.campus.student.domain;
import java.util.*;
public interface StudentRepository { Student save(Student student); Student saveMutation(Student student, long expectedVersion); Optional<Student> findById(UUID id); boolean existsByStudentNumber(String number); List<Student> findAll(); }
