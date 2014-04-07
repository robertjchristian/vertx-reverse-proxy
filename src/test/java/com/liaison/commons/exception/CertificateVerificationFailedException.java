package com.liaison.commons.exception;

public class CertificateVerificationFailedException extends Exception {

    private static final long serialVersionUID = 8843287752882801482L;

    public CertificateVerificationFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public CertificateVerificationFailedException(String message) {
        super(message);
    }

    public CertificateVerificationFailedException(Throwable cause) {
        super(cause);
    }
}