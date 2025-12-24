package com.simulator.moto6809.Errors;

public class Response {

    protected boolean success;
    protected String message;

    public Response() {
        this.success = true;
        this.message = "";
    }

    public Response(boolean success, String message) {
        this.success = success;
        this.message = message == null ? "" : message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public static Response ok() {
        return new Response(true, "");
    }

    public static Response error(String message) {
        return new Response(false, message);
    }
}


