package com.simulator.moto6809.Errors;

public class ResponseT<T> extends Response {

    private final T responseData;

    // SUCCESS with data
    public ResponseT(T responseData) {
        super(true, "");
        this.responseData = responseData;
    }

    // SUCCESS with message + data
    public ResponseT(String message, T responseData) {
        super(true, message);
        this.responseData = responseData;
    }

    // ERROR (no data)
    public ResponseT(boolean success, String message) {
        super(success, message);
        this.responseData = null;
    }

    public T getResponseData() {
        return responseData;
    }
}

