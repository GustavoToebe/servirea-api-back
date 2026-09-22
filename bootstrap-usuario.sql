INSERT INTO public.usuario (email, senha_hash, nome, ativo)
VALUES ('teste@teste.com', '{bcrypt}$2b$12$ksoV.8bO/iaebVXKnYv9EeagFxY6sdsAdLbmE9haTpVR.uvBngHqi', 'Usuario de Teste', true);

INSERT INTO public.usuario_tenant (usuario_id, tenant_id, role, status)
SELECT u.id, t.id, 'COORDENADOR', 'ATIVO'
FROM public.usuario u, public.tenant t
WHERE u.email = 'teste@teste.com' AND t.slug = 'teste-turnstile';
