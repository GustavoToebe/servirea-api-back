-- V022__table_password_reset_token.sql
-- Fase 5 (seção 105/32): tabela nova, não prevista nas migrations V016-V021
-- da Fase 3 (que só cobriam tenant/usuario/usuario_tenant/refresh_token) -
-- precisa existir para POST /auth/forgot-password e POST /auth/reset-password
-- funcionarem. Global (seção 25), mesmo padrão do refresh_token (V019):
-- nunca armazenar o token puro, só o hash; token de uso único (used_at).

CREATE TABLE public.password_reset_token (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    uuid NOT NULL REFERENCES public.usuario(id) ON DELETE CASCADE,
    token_hash    text NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    expires_at    timestamptz NOT NULL,
    used_at       timestamptz,
    CONSTRAINT password_reset_token_token_hash_key UNIQUE (token_hash)
);

COMMENT ON TABLE public.password_reset_token IS
    'Tokens de "esqueci minha senha" (seção 32 do plano mestre) - nunca armazenar o token puro, só o hash (token_hash). used_at marca uso único: uma vez consumido (ou expirado), o token não pode ser reaproveitado.';

CREATE INDEX idx_password_reset_token_usuario_id ON public.password_reset_token (usuario_id);
CREATE INDEX idx_password_reset_token_expires_at ON public.password_reset_token (expires_at);
