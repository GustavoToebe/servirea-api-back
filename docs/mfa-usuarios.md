# MFA e recuperação das contas do Servirea

Conta global: protege todos os vínculos do mesmo usuário. Migration V075; implementação na branch de melhorias, sem implantação. MFA opt-in, não uma permissão concedida pela paróquia.

## Uso

Meu perfil → Verificação em duas etapas. Informar senha atual, preparar autenticador, cadastrar manualmente a chave (conta Servirea, TOTP SHA-1, seis dígitos, 30 segundos) e confirmar em dez minutos. Sem QR por serviço externo. Guardar dez códigos aleatórios de recuperação antes de sair: aparecem uma vez e cada código funciona uma vez. O fator usado na ativação já foi consumido; no próximo login usar o código seguinte do autenticador ou uma recuperação.

Renovar recuperação exige senha e um segundo fator válido, substitui todo o lote e encerra sessões. Desativar também exige senha e fator. Perder autenticador e todos os códigos não é resolvido por redefinição por e-mail; não existe bypass público ou de suporte. Recuperação assistida com identidade verificada ainda depende de procedimento operacional, sem reset automático por operador.

## Contratos

- GET /me/mfa: ativo, configurado, codigosRestantes. Sem segredo.
- POST /me/mfa/preparar: {senha}; devolve segredo e expiraEm.
- POST /me/mfa/ativar: {senha,codigo}; devolve {codigos} uma vez.
- POST /me/mfa/desativar: {senha,codigo}; 204.
- POST /me/mfa/recuperacao: {senha,codigo}; novo lote uma vez.
- POST /auth/login: codigoMfa opcional para conta sem MFA; obrigatório quando ativo, também antes da seleção de paróquia.
- POST /auth/reset-password: token, novaSenha e codigoMfa quando ativo. O link não desativa MFA; falha não consome link nem fator.
- PUT /me: senhaAtual e codigoMfa exigidos para trocar senha quando aplicáveis; nova senha entre 8 e 72 caracteres. Atualizar dados sem trocar senha continua disponível.

Rotas de proteção própria exigem autenticação pessoal; suporte_app é recusado. Não recebem id de outra conta. Cache-Control no-store nas respostas de configuração. Erros MFA_NECESSARIO, MFA_INVALIDO, MFA_SENHA_INVALIDA e MFA_INDISPONIVEL; o interceptor não renova nem repete operações nesses erros. Login, configuração e recuperação têm limites por IP/conta com Retry-After, por instância. Contadores compartilhados entre réplicas permanecem uma evolução.

## Segurança e operação

Segredos ativo/pendente em AES-256-GCM, nonce aleatório, AAD com o UUID da conta. Recuperações de 128 bits guardadas só em SHA-256. Último passo TOTP impede replay. Lock global FOR NO KEY UPDATE no usuario serializa MFA, seleção de paróquia, login, troca de senha e rotação de refresh. A versão de credenciais no bearer/seleção invalida sessões anteriores imediatamente. Revogação por alteração da conta é atômica; proteção de reuso de refresh mantém a transação independente existente.

SERVIRE_MFA_CHAVES: lista id:base64, chaves de 32 bytes. SERVIRE_MFA_CHAVE_ATIVA: id presente na lista. Valores reais somente no ambiente/cofre; diferentes de JWT/HMAC/WhatsApp. Sem configuração, preparação é 503; contas protegidas não entram só com senha quando a chave está ausente. Manter chaves antigas enquanto usadas por registros/backups; recifragem em lote e política obrigatória ainda não estão implementadas. Relógio do servidor sincronizado.

O roteiro ambiente-local-atual.ps1 gera uma chave aleatória exclusivamente local em USERPROFILE/.servirea-mfa-local.key, separada da Central. Preservar esse arquivo junto do ambiente local; não versioná-lo nem reutilizá-lo em produção. O stub de e-mail não registra tokens nem em DEBUG e não entrega o link; recuperação por e-mail exige provedor/captura de testes.

Base técnica: [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238) e [OWASP MFA](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html). Testes cobrem vetores, replay, cifra/AAD/adulteração, expiração, HTTP, revogação, renovação de recuperação, consumo concorrente, refresh aguardando ativação, seleção antiga, suporte e redefinição protegida. O guia da rodada registra resultados efetivamente executados.
