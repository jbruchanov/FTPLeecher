package com.scurab.java.ftpleecher;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 11.2.13
 * Time: 21:26
 * To change this template use File | Settings | File Templates.
 */
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
