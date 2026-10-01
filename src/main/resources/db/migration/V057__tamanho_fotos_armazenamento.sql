-- Metadados locais para cotas; arquivos antigos ficam desconhecidos até conferência no provedor.
ALTER TABLE voluntarios ADD COLUMN foto_tamanho_bytes bigint CHECK (foto_tamanho_bytes > 0);
ALTER TABLE inscricoes ADD COLUMN foto_tamanho_bytes bigint CHECK (foto_tamanho_bytes > 0);
ALTER TABLE evento_foto ADD COLUMN foto_tamanho_bytes bigint CHECK (foto_tamanho_bytes > 0);
