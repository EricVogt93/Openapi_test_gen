package io.oatg;

/** Configuration or environment error that should abort the run with exit code 2. */
public class OatgException extends RuntimeException {

    public OatgException(String message) {
        super(message);
    }

    public OatgException(String message, Throwable cause) {
        super(message, cause);
    }
}
