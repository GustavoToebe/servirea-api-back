-- Suporte a layouts e lembretes multiselect no módulo de eventos (01/10/2026)
-- 1. Permite o novo tipo de layout 'EVENTO' na tabela layout_envio
ALTER TABLE public.layout_envio DROP CONSTRAINT IF EXISTS layout_envio_tipo_layout_check;
ALTER TABLE public.layout_envio ADD CONSTRAINT layout_envio_tipo_layout_check
    CHECK (tipo_layout IN ('TODOS','RESPONSAVEL','COROINHA','ACOLITO','COROINHA_ACOLITO','MINISTRO','EVENTO'));

-- 2. Altera a tabela evento para comportar envio por WhatsApp e E-mail com layouts
ALTER TABLE public.evento
    ADD COLUMN IF NOT EXISTS whatsapp_habilitado boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS whatsapp_layout_confirmacao_id uuid,
    ADD COLUMN IF NOT EXISTS whatsapp_layout_lembrete_id uuid,
    ADD COLUMN IF NOT EXISTS email_habilitado boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS email_layout_confirmacao_id uuid,
    ADD COLUMN IF NOT EXISTS email_layout_lembrete_id uuid;

ALTER TABLE public.evento
    ADD CONSTRAINT fk_evento_whatsapp_confirmacao FOREIGN KEY (tenant_id, whatsapp_layout_confirmacao_id)
        REFERENCES public.layout_envio (tenant_id, id) ON DELETE SET NULL (whatsapp_layout_confirmacao_id),
    ADD CONSTRAINT fk_evento_whatsapp_lembrete FOREIGN KEY (tenant_id, whatsapp_layout_lembrete_id)
        REFERENCES public.layout_envio (tenant_id, id) ON DELETE SET NULL (whatsapp_layout_lembrete_id),
    ADD CONSTRAINT fk_evento_email_confirmacao FOREIGN KEY (tenant_id, email_layout_confirmacao_id)
        REFERENCES public.layout_envio (tenant_id, id) ON DELETE SET NULL (email_layout_confirmacao_id),
    ADD CONSTRAINT fk_evento_email_lembrete FOREIGN KEY (tenant_id, email_layout_lembrete_id)
        REFERENCES public.layout_envio (tenant_id, id) ON DELETE SET NULL (email_layout_lembrete_id);

-- 3. Altera lembrete_dias de integer para varchar(50) para permitir múltiplos dias selecionados
ALTER TABLE public.evento DROP CONSTRAINT IF EXISTS evento_lembrete_dias_check;
ALTER TABLE public.evento ALTER COLUMN lembrete_dias TYPE varchar(50) USING lembrete_dias::varchar(50);
ALTER TABLE public.evento ALTER COLUMN lembrete_dias SET DEFAULT '1';

-- 4. Torna mensagens legadas opcionais (layouts passam a ser a fonte de verdade)
ALTER TABLE public.evento ALTER COLUMN mensagem_confirmacao DROP NOT NULL;
ALTER TABLE public.evento ALTER COLUMN mensagem_lembrete DROP NOT NULL;

-- 5. Adiciona campos de rastreamento de e-mail na inscrição
ALTER TABLE public.evento_inscricao
    ADD COLUMN IF NOT EXISTS confirmacao_email_destinatario_id uuid,
    ADD COLUMN IF NOT EXISTS lembrete_email_destinatario_id uuid,
    ADD COLUMN IF NOT EXISTS lembrete_email_enviado_em timestamptz;

-- 6. Cria layouts padrão de EVENTO para cada paróquia cadastrada, se ainda não existirem.
--    Eventos que já existem continuam com o texto que têm (layout nulo = texto padrão do sistema).
INSERT INTO public.layout_envio (id, tenant_id, nome, tipo_layout, tipo_envio, assunto, conteudo, ativo)
SELECT gen_random_uuid(), t.id, 'Confirmação de Inscrição em Evento (WhatsApp)', 'EVENTO', 'WHATSAPP', NULL,
       'Olá #PESSOA.PRIMEIRO_NOME#!' || chr(10) ||
       'Sua inscrição no evento *#EVENTO.TITULO#* está confirmada.' || chr(10) ||
       '📅 #EVENTO.DATA# às #EVENTO.HORA#' || chr(10) ||
       '📍 #EVENTO.LOCAL#' || chr(10) ||
       '#EVENTO.ENDERECO#' || chr(10) ||
       'Mapa: #EVENTO.MAPA#' || chr(10) ||
       'Dúvidas: #EVENTO.RESPONSAVEL# #EVENTO.TELEFONE#' || chr(10) ||
       '#PAROQUIA.NOME#', true
FROM public.tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM public.layout_envio l WHERE l.tenant_id = t.id AND l.nome = 'Confirmação de Inscrição em Evento (WhatsApp)'
);

INSERT INTO public.layout_envio (id, tenant_id, nome, tipo_layout, tipo_envio, assunto, conteudo, ativo)
SELECT gen_random_uuid(), t.id, 'Lembrete de Evento (WhatsApp)', 'EVENTO', 'WHATSAPP', NULL,
       'Olá #PESSOA.PRIMEIRO_NOME#! Lembrete: o evento *#EVENTO.TITULO#* é #EVENTO.QUANDO# (#EVENTO.DATA# às #EVENTO.HORA#).' || chr(10) ||
       '📍 #EVENTO.LOCAL#' || chr(10) ||
       '#EVENTO.ENDERECO#' || chr(10) ||
       'Mapa: #EVENTO.MAPA#' || chr(10) ||
       'Dúvidas: #EVENTO.RESPONSAVEL# #EVENTO.TELEFONE#' || chr(10) ||
       '#PAROQUIA.NOME#', true
FROM public.tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM public.layout_envio l WHERE l.tenant_id = t.id AND l.nome = 'Lembrete de Evento (WhatsApp)'
);

INSERT INTO public.layout_envio (id, tenant_id, nome, tipo_layout, tipo_envio, assunto, conteudo, ativo)
SELECT gen_random_uuid(), t.id, 'Confirmação de Inscrição em Evento (E-mail)', 'EVENTO', 'EMAIL',
       'Inscrição confirmada: #EVENTO.TITULO#',
       '<p>Olá, <strong>#PESSOA.PRIMEIRO_NOME#</strong>!</p><p>Sua inscrição no evento <strong>#EVENTO.TITULO#</strong> está confirmada.</p><p>📅 <strong>Data:</strong> #EVENTO.DATA# às #EVENTO.HORA#</p><p>📍 <strong>Local:</strong> #EVENTO.LOCAL#</p><p>#EVENTO.ENDERECO#</p><p>Dúvidas: #EVENTO.RESPONSAVEL# #EVENTO.TELEFONE#</p><p>Atenciosamente,<br>#PAROQUIA.NOME#</p>', true
FROM public.tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM public.layout_envio l WHERE l.tenant_id = t.id AND l.nome = 'Confirmação de Inscrição em Evento (E-mail)'
);

INSERT INTO public.layout_envio (id, tenant_id, nome, tipo_layout, tipo_envio, assunto, conteudo, ativo)
SELECT gen_random_uuid(), t.id, 'Lembrete de Evento (E-mail)', 'EVENTO', 'EMAIL',
       'Lembrete: evento #EVENTO.TITULO# #EVENTO.QUANDO#',
       '<p>Olá, <strong>#PESSOA.PRIMEIRO_NOME#</strong>!</p><p>Lembrete: o evento <strong>#EVENTO.TITULO#</strong> acontecerá <strong>#EVENTO.QUANDO#</strong> (#EVENTO.DATA# às #EVENTO.HORA#).</p><p>📍 <strong>Local:</strong> #EVENTO.LOCAL#</p><p>#EVENTO.ENDERECO#</p><p>Dúvidas: #EVENTO.RESPONSAVEL# #EVENTO.TELEFONE#</p><p>Atenciosamente,<br>#PAROQUIA.NOME#</p>', true
FROM public.tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM public.layout_envio l WHERE l.tenant_id = t.id AND l.nome = 'Lembrete de Evento (E-mail)'
);
