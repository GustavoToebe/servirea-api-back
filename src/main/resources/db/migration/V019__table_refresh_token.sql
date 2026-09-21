-- V019__table_refresh_token.sql
-- Fase 3 (seção 103/126, seção 35/36): refresh tokens com rotation. Tabela
-- global (seção 25) - o refresh token pertence ao usuário, não a um tenant
-- específico (o mesmo usuário pode alternar entre paróquias diferentes
-- sem precisar de um refresh token por paróquia).

CREATE TABLE public.refresh_token (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    uuid NOT NULL REFERENCES public.usuario(id) ON DELETE CASCADE,
    token_hash    text NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    expires_at    timestamptz NOT NULL,
    revoked_at    timestamptz,
    replaced_by   uuid REFERENCES public.refresh_token(id) ON DELETE SET NULL,
    ip            text,
    user_agent    text,
    CONSTRAINT refresh_token_token_hash_key UNIQUE (token_hash)
);

COMMENT ON TABLE public.refresh_token IS
    'Refresh tokens com rotation (seção 36 do plano mestre) - nunca armazenar o token puro, só o hash (token_hash). Reutilização de um token já revogado (revoked_at preenchido) deve ser tratada como evento de segurança quando a Fase 5 implementar o fluxo de refresh.';

CREATE INDEX idx_refresh_token_usuario_id ON public.refresh_token (usuario_id);
CREATE INDEX idx_refresh_token_expires_at ON public.refresh_token (expires_at);
