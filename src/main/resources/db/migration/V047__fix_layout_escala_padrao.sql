DO $$
DECLARE
    semanal_json jsonb := '[{"ordem": 1, "funcao": "MISSAL", "posicao": 1, "rotulo": "Acólito Missal"}, {"ordem": 2, "funcao": "CRUZ", "posicao": 1, "rotulo": "Cruz"}, {"ordem": 3, "funcao": "CREDENCIA", "posicao": 1, "rotulo": "Credência"}, {"ordem": 4, "funcao": "VELA", "posicao": 1, "rotulo": "Vela 1"}, {"ordem": 5, "funcao": "VELA", "posicao": 2, "rotulo": "Vela 2"}, {"ordem": 6, "funcao": "SINO", "posicao": 1, "rotulo": "Sino 1"}, {"ordem": 7, "funcao": "SINO", "posicao": 2, "rotulo": "Sino 2"}]'::jsonb;
    mensal_json jsonb := '[{"ordem": 1, "funcao": "MISSAL", "posicao": 1, "rotulo": "Acólito Missal"}, {"ordem": 2, "funcao": "CRUZ", "posicao": 1, "rotulo": "Cruz"}, {"ordem": 3, "funcao": "CREDENCIA", "posicao": 1, "rotulo": "Credência"}, {"ordem": 4, "funcao": "VELA", "posicao": 1, "rotulo": "Vela 1"}, {"ordem": 5, "funcao": "VELA", "posicao": 2, "rotulo": "Vela 2"}, {"ordem": 6, "funcao": "COLETA", "posicao": 1, "rotulo": "Coleta"}, {"ordem": 7, "funcao": "COLETA", "posicao": 2, "rotulo": "Coleta"}, {"ordem": 8, "funcao": "COLETA", "posicao": 3, "rotulo": "Coleta"}, {"ordem": 9, "funcao": "COLETA", "posicao": 4, "rotulo": "Coleta"}, {"ordem": 10, "funcao": "SINO", "posicao": 1, "rotulo": "Sino 1"}, {"ordem": 11, "funcao": "SINO", "posicao": 2, "rotulo": "Sino 2"}]'::jsonb;
    
    r RECORD;
    ev_id uuid;
    json_cols jsonb;
    v_funcao text;
    v_qtd int;
    i int;
    v_ordem int;
    label text;
BEGIN
    -- Update factory defaults that were incorrectly seeded as []
    UPDATE public.layout_escala SET colunas = semanal_json WHERE tipo = 'SEMANAL' AND sistema = true AND colunas = '[]'::jsonb;
    UPDATE public.layout_escala SET colunas = mensal_json WHERE tipo = 'MENSAL' AND sistema = true AND colunas = '[]'::jsonb;

    -- Update existing scales that were incorrectly backfilled without posicao and ordem
    FOR r IN (SELECT id FROM public.escalas) LOOP
        -- check if it is missing posicao (the old V046 inserted without posicao)
        IF (SELECT colunas->0->>'posicao' FROM public.escalas WHERE id = r.id) IS NULL AND (SELECT jsonb_array_length(colunas) FROM public.escalas WHERE id = r.id) > 0 THEN
            
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
        END IF;
    END LOOP;
END
$$;
