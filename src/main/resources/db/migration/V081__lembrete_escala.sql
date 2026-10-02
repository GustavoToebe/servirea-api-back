ALTER TABLE notificacao_config DROP CONSTRAINT notificacao_config_origem_check,
 ADD CONSTRAINT notificacao_config_origem_check CHECK(origem IN ('ESCALA','MURAL','ESCALA_LEMBRETE'));
ALTER TABLE notificacao_entrega DROP CONSTRAINT notificacao_entrega_origem_check,
 ADD CONSTRAINT notificacao_entrega_origem_check CHECK(origem IN ('ESCALA','MURAL','ESCALA_LEMBRETE'));
