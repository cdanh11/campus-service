package com.campus.student.domain;
public record StudentSearch(int page,int size,String query,StudentStatus status,String sortField,boolean ascending) { }
