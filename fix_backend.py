import re

semanal_json = '[{"ordem": 1, "funcao": "MISSAL", "posicao": 1, "rotulo": "Acólito Missal"}, {"ordem": 2, "funcao": "CRUZ", "posicao": 1, "rotulo": "Cruz"}, {"ordem": 3, "funcao": "CREDENCIA", "posicao": 1, "rotulo": "Credência"}, {"ordem": 4, "funcao": "VELA", "posicao": 1, "rotulo": "Vela 1"}, {"ordem": 5, "funcao": "VELA", "posicao": 2, "rotulo": "Vela 2"}, {"ordem": 6, "funcao": "SINO", "posicao": 1, "rotulo": "Sino 1"}, {"ordem": 7, "funcao": "SINO", "posicao": 2, "rotulo": "Sino 2"}]'

mensal_json = '[{"ordem": 1, "funcao": "MISSAL", "posicao": 1, "rotulo": "Acólito Missal"}, {"ordem": 2, "funcao": "CRUZ", "posicao": 1, "rotulo": "Cruz"}, {"ordem": 3, "funcao": "CREDENCIA", "posicao": 1, "rotulo": "Credência"}, {"ordem": 4, "funcao": "VELA", "posicao": 1, "rotulo": "Vela 1"}, {"ordem": 5, "funcao": "VELA", "posicao": 2, "rotulo": "Vela 2"}, {"ordem": 6, "funcao": "COLETA", "posicao": 1, "rotulo": "Coleta"}, {"ordem": 7, "funcao": "COLETA", "posicao": 2, "rotulo": "Coleta"}, {"ordem": 8, "funcao": "COLETA", "posicao": 3, "rotulo": "Coleta"}, {"ordem": 9, "funcao": "COLETA", "posicao": 4, "rotulo": "Coleta"}, {"ordem": 10, "funcao": "SINO", "posicao": 1, "rotulo": "Sino 1"}, {"ordem": 11, "funcao": "SINO", "posicao": 2, "rotulo": "Sino 2"}]'

sql_content = f'''CREATE TABLE public.layout_escala (
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
    v_ordem int;
    label text;
BEGIN
    FOR r IN (SELECT id FROM public.escalas WHERE colunas IS NULL) LOOP
        SELECT id INTO ev_id FROM public.escala_eventos WHERE escala_id = r.id AND referencia = false ORDER BY data ASC, horario ASC LIMIT 1;
        
        json_cols := '[]'::jsonb;
        v_ordem := 1;
        
        IF ev_id IS NOT NULL THEN
            FOR v_funcao IN SELECT unnest(ARRAY['MISSAL', 'CRUZ', 'CREDENCIA', 'VELA', 'COLETA', 'SINO', 'OUTRO']) LOOP
                SELECT count(*) INTO v_qtd FROM public.escala_vagas WHERE evento_id = ev_id AND funcao = v_funcao::public.funcao_escala;
                IF v_qtd > 0 THEN
                    FOR i IN 1..v_qtd LOOP
                        IF v_funcao = 'MISSAL' THEN
                            label := 'Acólito Missal';
                        ELSIF v_funcao = 'CRUZ' THEN
                            label := 'Cruz';
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
                        
                        json_cols := json_cols || jsonb_build_object('ordem', v_ordem, 'funcao', v_funcao, 'posicao', i, 'rotulo', label);
                        v_ordem := v_ordem + 1;
                    END LOOP;
                END IF;
            END LOOP;
        END IF;
        
        UPDATE public.escalas SET colunas = json_cols WHERE id = r.id;
    END LOOP;
END
$$;

INSERT INTO public.layout_escala (tenant_id, nome, tipo, colunas, sistema, ativo)
SELECT id, 'Padrão Semanal', 'SEMANAL', '{semanal_json}'::jsonb, true, true FROM public.tenant;

INSERT INTO public.layout_escala (tenant_id, nome, tipo, colunas, sistema, ativo)
SELECT id, 'Padrão Mensal', 'MENSAL', '{mensal_json}'::jsonb, true, true FROM public.tenant;
'''

with open('src/main/resources/db/migration/V046__layout_escala.sql', 'w', encoding='utf-8') as f:
    f.write(sql_content)

import os
os.system("git checkout src/main/java/br/com/servire/api/integracao/IntegracaoInstanciaService.java")

with open('src/main/java/br/com/servire/api/integracao/IntegracaoInstanciaService.java', 'r', encoding='utf-8') as f:
    java = f.read()

s_json_esc = semanal_json.replace('"', '\\"')
m_json_esc = mensal_json.replace('"', '\\"')

new_block = f"""
        transacao.execute(status -> {{
            jdbcTemplate.update("INSERT INTO public.layout_escala (tenant_id, nome, tipo, colunas, sistema, ativo) VALUES (?, 'Padrão Semanal', 'SEMANAL', ?::jsonb, true, true)", tenantId, "{s_json_esc}");
            jdbcTemplate.update("INSERT INTO public.layout_escala (tenant_id, nome, tipo, colunas, sistema, ativo) VALUES (?, 'Padrão Mensal', 'MENSAL', ?::jsonb, true, true)", tenantId, "{m_json_esc}");
            return null;
        }});
"""

java = re.sub(r"transacao\.execute\(status -> \{[\s\S]*?return null;\s*\}\);", new_block.strip(), java)

with open('src/main/java/br/com/servire/api/integracao/IntegracaoInstanciaService.java', 'w', encoding='utf-8') as f:
    f.write(java)
