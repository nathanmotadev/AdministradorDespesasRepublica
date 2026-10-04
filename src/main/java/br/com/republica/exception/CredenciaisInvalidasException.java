package br.com.republica.exception;

/** E-mail ou senha incorretos (HTTP 401). A mensagem é a mesma nos dois casos, de propósito. */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha incorretos.");
    }
}
