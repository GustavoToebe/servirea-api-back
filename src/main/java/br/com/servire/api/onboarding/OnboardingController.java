package br.com.servire.api.onboarding;
import br.com.servire.api.onboarding.dto.OnboardingDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/onboarding")
public class OnboardingController {
 private final OnboardingService service;
 public OnboardingController(OnboardingService service){this.service=service;}
 @GetMapping @PreAuthorize("hasAuthority('PERM_ONBOARDING')") public Resposta buscar(){return service.buscar();}
 @PutMapping("/etapas/{codigo}") @PreAuthorize("hasAuthority('PERM_ONBOARDING') and hasAuthority('PERM_ONBOARDING_GERENCIAR')") public Resposta alterar(@PathVariable EtapaOnboarding codigo,@Valid @RequestBody Salvar req){return service.alterar(codigo,req);}
}
