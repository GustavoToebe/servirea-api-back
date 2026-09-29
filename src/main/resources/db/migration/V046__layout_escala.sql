CREATE TABLE public.layout_escala (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    nome varchar(255) NOT NULL,
    tipo public.tipo_escala NOT NULL,
    colunas jsonb NOT NULL,
    ativo boolean NOT NULL DEFAULT true,
    sistema boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX uq_layout_escala_nome_tenant_tipo ON public.layout_escala (tenant_id, tipo, lower(nome));

ALTER TABLE public.layout_escala ENABLE ROW LEVEL SECURITY;

CREATE TRIGGER trg_layout_escala_updated_at
    BEFORE UPDATE ON public.layout_escala
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.escalas ADD COLUMN colunas jsonb;
ALTER TABLE public.escalas ADD COLUMN layout_id uuid REFERENCES public.layout_escala(id);

DO $$
DECLARE
    r RECORD;
    ev_id uuid;
    json_cols jsonb;
    v_funcao text;
    v_qtd int;
    i int;
    label text;
BEGIN
    FOR r IN (SELECT id FROM public.escalas WHERE colunas IS NULL) LOOP
        SELECT id INTO ev_id FROM public.escala_eventos WHERE escala_id = r.id AND referencia = false ORDER BY data ASC, horario ASC LIMIT 1;
        
        json_cols := '[]'::jsonb;
        
        IF ev_id IS NOT NULL THEN
            FOR v_funcao IN SELECT unnest(ARRAY['MISSAL', 'CRUZ', 'CREDENCIA', 'VELA', 'COLETA', 'SINO', 'OUTRO']) LOOP
                SELECT count(*) INTO v_qtd FROM public.escala_vagas WHERE evento_id = ev_id AND funcao = v_funcao::public.funcao_escala;
                IF v_qtd > 0 THEN
                    FOR i IN 1..v_qtd LOOP
                        IF v_funcao = 'MISSAL' THEN
                            label := 'Acólito Missal';
                        ELSIF v_funcao = 'CRUZ' THEN
                            label := 'Cruciferário';
                        ELSIF v_funcao = 'CREDENCIA' THEN
                            label := 'Credência';
                        ELSIF v_funcao = 'VELA' THEN
                            label := 'Vela';
                        ELSIF v_funcao = 'COLETA' THEN
                            label := 'Coleta';
                        ELSIF v_funcao = 'SINO' THEN
                            label := 'Sino';
                        ELSIF v_funcao = 'OUTRO' THEN
                            label := 'Outro';
                        END IF;
                        
                        IF v_qtd > 1 THEN
                            label := label || ' ' || i;
                        END IF;
                        
                        json_cols := json_cols || jsonb_build_object('funcao', v_funcao, 'rotulo', label);
                    END LOOP;
                END IF;
            END LOOP;
        END IF;
        
        UPDATE public.escalas SET colunas = json_cols WHERE id = r.id;
    END LOOP;
END
$$;

INSERT INTO public.layout_escala (tenant_id, nome, tipo, colunas, sistema, ativo)
SELECT id, 'Padrão Semanal', 'SEMANAL', '[]'::jsonb, true, true FROM public.tenant;

INSERT INTO public.layout_escala (tenant_id, nome, tipo, colunas, sistema, ativo)
SELECT id, 'Padrão Mensal', 'MENSAL', '[]'::jsonb, true, true FROM public.tenant;
