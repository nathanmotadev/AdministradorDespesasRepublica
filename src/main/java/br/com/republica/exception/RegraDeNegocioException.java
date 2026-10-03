package br.com.republica.exception;

/** Pedido válido na forma, mas que viola uma regra do domínio (vira HTTP 422). */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
