package com.scurab.java.ftpleecher;

public class FatalFTPException extends Exception {

    public FatalFTPException(String message) {
        super(message);
    }

    public FatalFTPException(String message, Throwable cause) {
        super(message, cause);
    }

    public FatalFTPException(Throwable cause) {
        super(cause);
    }
}
