package br.com.republica.exception;

/** O usuário está logado, mas não tem permissão para esta ação (HTTP 403). */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
