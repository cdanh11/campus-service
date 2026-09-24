package com.campus.personnel.domain;
public record FacultyStaffSearch(int page,int size,String query,PersonnelStatus status,PersonnelType personnelType,String sortField,boolean ascending) { }
