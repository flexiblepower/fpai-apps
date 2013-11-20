package de.fraunhofer.itwm.hexabus;

import java.io.IOException;

public class NoResponseException extends IOException {
    private static final long serialVersionUID = 5471604169896269810L;

    public NoResponseException() {
        super();
    }

    public NoResponseException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoResponseException(String message) {
        super(message);
    }

    public NoResponseException(Throwable cause) {
        super(cause);
    }
}
