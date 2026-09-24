package com.campus.personnel.domain;
import java.util.*; public interface FacultyStaffRepository { FacultyStaffMember save(FacultyStaffMember member); FacultyStaffMember saveMutation(FacultyStaffMember member,long version); Optional<FacultyStaffMember> findById(UUID id); boolean existsByPersonnelNumber(String number); List<FacultyStaffMember> findAll(); }
