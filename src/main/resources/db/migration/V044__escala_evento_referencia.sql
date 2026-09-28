-- Linha de referência da escala replicada (28/09/2026): dia da escala de origem que não existe no mês novo.
-- Guarda os nomes para a responsável repor; nunca é publicada, exportada nem conta em relatório.
ALTER TABLE public.escala_eventos ADD COLUMN referencia boolean NOT NULL DEFAULT false;
