-- Mandato ministerial no voluntário e cota de servidores por diocese.
-- A cota conta voluntários ativos das paróquias vinculadas. Não é cota
-- de mensagem. Nulo em cota_voluntarios = sem teto.

ALTER TABLE public.voluntarios
    ADD COLUMN mandato_inicio date,
    ADD COLUMN mandato_fim date,
    ADD CONSTRAINT voluntarios_mandato_ordem
        CHECK (mandato_fim IS NULL OR mandato_inicio IS NULL OR mandato_fim >= mandato_inicio);

COMMENT ON COLUMN public.voluntarios.mandato_inicio IS
    'Data de investidura do mandato. Opcional; usada sobretudo no MESC.';
COMMENT ON COLUMN public.voluntarios.mandato_fim IS
    'Vencimento do mandato diocesano. Nulo = sem prazo cadastrado.';

CREATE TABLE public.diocese (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nome               text NOT NULL,
    uf                 text,
    cota_voluntarios   integer CHECK (cota_voluntarios IS NULL OR cota_voluntarios > 0),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT diocese_nome_key UNIQUE (nome)
);

COMMENT ON TABLE public.diocese IS
    'Diocese que agrupa paróquias. cota_voluntarios é o teto de servidores ativos somados nessas paróquias.';
COMMENT ON COLUMN public.diocese.cota_voluntarios IS
    'Teto de voluntários ativos. Nulo = sem teto. Baixar a cota não desativa quem já está ativo.';

CREATE TRIGGER trg_diocese_updated_at
    BEFORE UPDATE ON public.diocese
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.diocese ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.tenant
    ADD COLUMN diocese_id uuid REFERENCES public.diocese(id),
    ADD COLUMN voluntarios_ativos integer NOT NULL DEFAULT 0;

CREATE INDEX idx_tenant_diocese ON public.tenant (diocese_id);

COMMENT ON COLUMN public.tenant.diocese_id IS
    'Diocese da paróquia. Nulo = sem vínculo; a cota diocesana não se aplica.';
COMMENT ON COLUMN public.tenant.voluntarios_ativos IS
    'Espelho dos voluntários ativos desta paróquia, mantido por trigger, para a cota da diocese sem varrer tabela de outro tenant no Java.';

UPDATE public.tenant t
   SET voluntarios_ativos = (
       SELECT COUNT(*)::integer
         FROM public.voluntarios v
        WHERE v.tenant_id = t.id
          AND v.ativo = true
   );

CREATE OR REPLACE FUNCTION public.recalcular_voluntarios_ativos()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    alvo uuid;
BEGIN
    alvo := COALESCE(NEW.tenant_id, OLD.tenant_id);
    UPDATE public.tenant
       SET voluntarios_ativos = (
           SELECT COUNT(*)::integer
             FROM public.voluntarios
            WHERE tenant_id = alvo
              AND ativo = true
       )
     WHERE id = alvo;
    IF TG_OP = 'UPDATE' AND OLD.tenant_id IS DISTINCT FROM NEW.tenant_id THEN
        UPDATE public.tenant
           SET voluntarios_ativos = (
               SELECT COUNT(*)::integer
                 FROM public.voluntarios
                WHERE tenant_id = OLD.tenant_id
                  AND ativo = true
           )
         WHERE id = OLD.tenant_id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_voluntarios_ativos
    AFTER INSERT OR UPDATE OR DELETE ON public.voluntarios
    FOR EACH ROW
    EXECUTE FUNCTION public.recalcular_voluntarios_ativos();
