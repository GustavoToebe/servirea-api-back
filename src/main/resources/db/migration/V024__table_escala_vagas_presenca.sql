-- V024__table_escala_vagas_presenca.sql
-- Controle de faltas (Fase 11 do plano mestre — item 11 da seção 122,
-- detalhado na seção 131.5: "novo status em escala_vaga ou tabela própria
-- falta"). Optou-se pela primeira alternativa (coluna em escala_vagas, não
-- tabela própria): presença é sempre um atributo de UMA vaga específica já
-- ocupada por um voluntário, nunca um registro independente com vida
-- própria — não há necessidade de uma tabela separada para isto.

CREATE TYPE public.presenca_vaga AS ENUM (
    'PENDENTE',
    'PRESENTE',
    'FALTOU'
);

ALTER TABLE public.escala_vagas
    ADD COLUMN presenca public.presenca_vaga NOT NULL DEFAULT 'PENDENTE';

COMMENT ON COLUMN public.escala_vagas.presenca IS
    'Registro de presença do voluntário nesta vaga, feito pelo coordenador após o evento acontecer. PENDENTE é o valor inicial (evento ainda não ocorreu, ou ainda não foi conferido) — não deve ser confundido com "faltou".';
