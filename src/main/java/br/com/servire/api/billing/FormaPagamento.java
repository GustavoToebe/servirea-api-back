package br.com.servire.api.billing;

/** Como o pagamento chegou (registro manual do operador, 131.3). Enum nativo {@code forma_pagamento} (V029). */
public enum FormaPagamento {
    PIX,
    CARTAO_CREDITO,
    CARTAO_DEBITO,
    DINHEIRO,
    TRANSFERENCIA,
    BOLETO,
    OUTRO
}
