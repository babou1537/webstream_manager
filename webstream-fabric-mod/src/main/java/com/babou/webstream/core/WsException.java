package com.babou.webstream.core;

/** Erreur métier : le code (ex. SCREEN_NOT_FOUND) est renvoyé tel quel à l'interface. */
public class WsException extends RuntimeException {
    private final String code;

    public WsException(String code) {
        super(code);
        this.code = code;
    }

    public WsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
