package com.campus.identity.domain;

/** Serializes mutations which could leave the system without an active administrator. */
public interface AdminGuardRepository {

    void lock();
}
