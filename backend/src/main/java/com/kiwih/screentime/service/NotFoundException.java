package com.kiwih.screentime.service;

/** Something referenced by id is not there. Maps to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super(what + " " + id + " does not exist.");
    }

    public NotFoundException(String message) {
        super(message);
    }
}
