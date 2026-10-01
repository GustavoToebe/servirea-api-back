package br.com.servire.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** AES-256-GCM: nonce aleatório por gravação, versão de chave e autenticação vinculada à paróquia. */
@Component
public class CifraCredencial {
    private final Map<String, SecretKeySpec> chaves = new HashMap<>();
    private final String ativa;
    private final SecureRandom aleatorio = new SecureRandom();
    public CifraCredencial(@Value("${servire.segredos.chaves:}") String configuracao,
                           @Value("${servire.segredos.chave-ativa:v1}") String ativa) {
        this.ativa = ativa;
        try {
            if (!ativa.matches("[a-zA-Z0-9_-]{1,32}")) throw new IllegalArgumentException();
            if (!configuracao.isBlank()) for (String entrada : configuracao.split(",")) {
                String[] partes = entrada.trim().split(":",2);
                byte[] bytes = Base64.getDecoder().decode(partes[1]);
                if (!partes[0].matches("[a-zA-Z0-9_-]{1,32}") || bytes.length != 32 || chaves.containsKey(partes[0])) throw new IllegalArgumentException();
                chaves.put(partes[0],new SecretKeySpec(bytes,"AES"));
            }
            if (!chaves.isEmpty() && !chaves.containsKey(ativa)) throw new IllegalArgumentException();
        } catch (RuntimeException ex) { throw new IllegalStateException("Configuração de cifra inválida. Confira CREDENCIAIS_CHAVES e CREDENCIAIS_CHAVE_ATIVA."); }
    }
    public boolean configurada() { return chaves.containsKey(ativa); }
    public String cifrar(UUID tenant, String texto) {
        if (!configurada()) throw new IllegalStateException("Configure a chave de cifra antes de cadastrar credenciais.");
        try {
            byte[] nonce=new byte[12]; aleatorio.nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,chaves.get(ativa),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad(tenant));
            return "enc:v1:"+ativa+":"+Base64.getEncoder().encodeToString(nonce)+":"+
                Base64.getEncoder().encodeToString(cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Não foi possível proteger a credencial."); }
    }
    public String abrir(UUID tenant, String texto) {
        try {
            String[] partes=texto.split(":",5);
            if (partes.length!=5 || !partes[0].equals("enc") || !partes[1].equals("v1")) throw new IllegalArgumentException();
            byte[] nonce=Base64.getDecoder().decode(partes[3]); if (nonce.length!=12) throw new IllegalArgumentException();
            var chave=chaves.get(partes[2]); if (chave==null) throw new IllegalArgumentException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,chave,new GCMParameterSpec(128,nonce)); cipher.updateAAD(aad(tenant));
            return new String(cipher.doFinal(Base64.getDecoder().decode(partes[4])),StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Credencial indisponível: chave ausente ou conteúdo inválido."); }
    }
    public boolean precisaRotacionar(String texto) { return !texto.startsWith("enc:v1:"+ativa+":"); }
    private static byte[] aad(UUID tenant) { return ("servirea/whatsapp/"+tenant).getBytes(StandardCharsets.UTF_8); }
}
