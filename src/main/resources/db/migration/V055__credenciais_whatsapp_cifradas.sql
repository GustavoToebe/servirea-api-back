-- Envelope AES-GCM inclui versão de chave e nonce; credenciais legadas são recifradas pela aplicação.
ALTER TABLE paroquia_whatsapp ALTER COLUMN token TYPE text;
